# REQ-20260507-004 Codex 真实 auth.json 入库与长期详测

状态：Done  
日期：2026-05-07  
关联任务：

- [TASK-20260507-016 Codex 真实 auth.json 长期测试账号入库与详测](../../tasks/done/TASK-20260507-016-codex-real-auth-db-long-term-test.md)

## 背景

用户确认 `C:/Users/zzp84/Desktop/auth.json` 是真实 Codex 测试凭证，可写入数据库用于长期测试。上一批已经完成 Codex `auth.json` parser、脱敏摘要和 dry-run smoke，但尚未把真实测试账号安全落入本地数据库，也缺少更详尽的 Codex 账号入库、幂等、刷新、smoke 和敏感信息防泄漏验证。

## 目标

- 将真实 Codex `auth.json` 以加密方式写入数据库，形成长期测试账号。
- 建立可重复执行的显式导入机制，避免重复账号和凭证泄漏。
- 扩展 Codex 官方账号测试，覆盖 parser、幂等导入、脱敏 metadata、刷新 snapshot、route block 和 smoke dry-run。
- 回写任务、验证结果和遗留风险。

## 范围

- 新增显式开启的 Codex 长期测试导入器，读取本机文件路径并通过现有加密服务保存 token。
- 自动创建或复用 `CODEX_OAUTH` 测试账号池。
- 通过多信号账号指纹幂等复用账号，重复执行更新加密 token 与脱敏 metadata，不新增重复账号。
- 账号重复判定不能只依赖 `auth.json` 中的 `account_id`；同一 Codex 账号重复登录可能产生不同 token、不同本地字段或不同 `account_id` 表现，需要优先使用稳定 subject/email，再回退到 `account_id`，token fingerprint 仅作为弱证据和审计线索。
- Codex App API 适配：
  - ChatGPT 登录态 Codex CLI/App 的 Responses endpoint 为 `https://chatgpt.com/backend-api/codex/responses`，不是标准 API key 的 `https://api.openai.com/v1/responses`。
  - 账号保活与额度探测使用 `GET https://chatgpt.com/backend-api/wham/usage`，需要携带 Bearer access token，并尽量携带 `ChatGPT-Account-Id`。
  - smoke 请求需模拟 Codex CLI/App：SSE、`openai-beta: responses=experimental`、`originator`、`session_id`、`conversation_id`、`prompt_cache_key`、`store: false`、`include: ["reasoning.encrypted_content"]`。
- 入库后输出账号 ID、账号池 ID、route eligibility、fingerprint 和状态，不输出真实 token。
- 增补后端测试。

## 非目标

- 不把真实 `auth.json` 原文、token、refresh token、id token 写入仓库。
- 不默认在应用启动时导入真实凭证；必须显式传入导入路径。
- 不默认触发真实联网请求；只有显式传入 `dryRun=false` 或 `gateway.codex-test.live-smoke=true` 才执行真实 smoke。
- Codex `auth.json` 是 Codex CLI/App 使用的 OAuth 凭证，不能简单等同于标准 OpenAI API key；真实 smoke 和账号保活需要对齐 Codex CLI 与参考项目使用的 App API。
- 不改变生产环境账号加密策略。

## 风险

- 真实凭证进入数据库后，数据库备份和访问权限需要按敏感数据管理。
- 默认本地加密 key 若变化，会影响后续解密，因此长期测试环境应保持 `GATEWAY_ENCRYPTION_KEY` 稳定。
- 官方 Codex token 可能过期，长期测试需要周期刷新或重新导入。

## 验收标准

- 可以通过显式参数将 `C:/Users/zzp84/Desktop/auth.json` 导入数据库。
- 重复导入同一 `account_id` 不产生重复 `upstream_account`。
- 数据库中的 access/refresh token 仅保存加密 ciphertext；metadata 和输出不包含真实 token。
- Codex 详测覆盖 parser、幂等导入、Codex App API dry-run、显式真实 smoke、账号保活/usage 探测、route block、刷新 snapshot 和安全输出。
- Codex App API 真实 smoke 不能把 `gpt-5-codex` 作为唯一固定模型；参考项目已将 Stream Check 默认探测模型切到 `gpt-5.4@low`，并把 `model_not_found`、`invalid model`、`model is not supported` 等 4xx 归类为模型不适配，而不是账号失效。
- `.\gradlew.bat test` 通过。

## 实施记录

- 2026-05-07：创建需求，确认真实凭证只允许加密入库和脱敏输出。
- 2026-05-07：用户允许使用真实 `auth.json` 做 Codex 测试，补充显式真实 smoke 范围；默认仍不自动联网，避免误消耗额度。
- 2026-05-07：补充账号重复判定要求：同一账号重复登录可能产生完全不同的 `auth.json`，需采用稳定身份指纹而不是单纯 token 或单一 `account_id`。
- 2026-05-07：用户指出该 `auth.json` 是 Codex 使用的凭证，真实测试应对齐 Codex CLI/App API；后续先查开源 Codex CLI 与参考项目账号保活实现，再校正 live smoke。
- 2026-05-07：核准官方 Codex CLI 与参考项目实现：ChatGPT 登录态走 `chatgpt.com/backend-api/codex/responses`，保活/额度走 `chatgpt.com/backend-api/wham/usage`；`cc-switch`、`cli_proxy`、`cockpit-tools` 均围绕 Codex App API、SSE、session headers 和 usage polling 实现。
- 2026-05-07：参考 `cc-switch` 的 Stream Check 默认 `gpt-5.4@low` 和模型错误分类，修正本任务模型策略：真实 smoke 默认使用 `gpt-5.4` + `reasoning.effort=low`，并将不支持模型的 4xx 作为可恢复配置问题处理。
- 2026-05-07：实现并验证完成：真实 `auth.json` 幂等更新到长期测试账号 `accountId=2`、账号池 `poolId=5`；`wham/usage` 保活返回 200；Codex App API responses 真实 smoke 返回 200，脱敏 requestId `9f7ef933bf6000b0-KIX`、responseId `resp_001b5803925e77b90169fc522c89708191a5c92e22468b9b1e`。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.OfficialAccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CodexAuthJsonParserTests" --tests "com.prodigalgal.xaigateway.admin.application.CodexLongTermTestImportServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CodexResponsesSmokeHttpClientTests"`：通过。
- `.\gradlew.bat bootRun --args="--spring.profiles.active=local --gateway.codex-test.import-auth-json-path=C:/Users/zzp84/Desktop/auth.json --gateway.codex-test.import-only=true --gateway.codex-test.live-smoke=true"`：通过，真实 smoke 状态 `LIVE_SMOKE_OK`，未在日志或文档中输出真实 token。
