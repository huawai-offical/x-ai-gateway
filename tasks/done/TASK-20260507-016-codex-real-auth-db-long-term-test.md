# TASK-20260507-016 Codex 真实 auth.json 长期测试账号入库与详测

状态：Done  
优先级：High  
排期：P0-05  
来源：User Request  
关联需求：[REQ-20260507-004 Codex 真实 auth.json 入库与长期详测](../../docs/requirements/REQ-20260507-004-codex-real-auth-db-long-term-test.md)

## 背景

上一批完成了 Codex `auth.json` parser 和 dry-run smoke，但真实凭证尚未进入数据库。用户已确认测试用真实 `auth.json` 可用于长期测试，需要补齐实际入库、幂等复用和更详尽 Codex 测试。

## 目标

- 将真实 Codex `auth.json` 写入数据库的加密账号字段。
- 建立可重复执行、显式开启、无明文输出的导入器。
- 增加 Codex 详测覆盖面，包含显式真实 Codex App API responses smoke 与 `wham/usage` 保活。
- 回写数据库入库结果和验证结果。

## 详细设计

- 新增 `CodexLongTermTestImportRunner`，仅当 `gateway.codex-test.import-auth-json-path` 存在时执行。
- runner 读取本机文件，调用官方账号服务的幂等导入方法。
- 自动创建或复用 `codex-long-term-test` 账号池，providerType 为 `CODEX_OAUTH`，allowedClientFamilies 为 `CODEX`。
- 幂等键升级为稳定身份指纹：OpenID/JWT subject、email、官方 account ID 优先；token fingerprint 仅作为弱证据，不单独合并。
- 输出仅包含账号 ID、账号池 ID、状态、routeBlockReason、fingerprint 和安全摘要。
- 导入过程按批量操作可信性处理：先解析和预检，再执行入库；重复执行必须幂等，失败只能输出脱敏原因，不能泄漏凭证。
- 扩展 `codexResponsesSmoke`：默认 dry-run；当 `dryRun=false` 时解密账号 token 并按 Codex CLI/App API 做真实 smoke，只保存 HTTP 状态、requestId、响应 ID、耗时和脱敏错误。
- 补充账号保活/usage 探测：对照开源 Codex CLI 与参考项目使用的 endpoint、header 和刷新节流策略，默认查询 `GET https://chatgpt.com/backend-api/wham/usage`，避免把标准 OpenAI API 的 401 误判为 Codex 账号失效。
- Codex App responses smoke 默认使用 `POST https://chatgpt.com/backend-api/codex/responses`，请求头包含 `accept: text/event-stream`、`openai-beta: responses=experimental`、`originator: codex_cli_rs`、`session_id`、`conversation_id`、`x-client-request-id`、`x-codex-window-id`；请求体包含 `store: false`、`stream: true`、`include: ["reasoning.encrypted_content"]` 和稳定 `prompt_cache_key`。
- 真实 smoke 的模型选择对齐参考项目：默认探测 `gpt-5.4@low` 语义，即请求模型为 `gpt-5.4` 且 `reasoning.effort=low`；若服务端返回模型不支持或不存在，只归类为 `MODEL_NOT_SUPPORTED`，不冻结账号、不扣为凭证失效。
- 支持显式 baseUrl 覆盖；只有明确指向 `api.openai.com` 或 `/v1` API key 模式时才走标准 `/v1/responses`，避免 Codex OAuth 凭证误打标准 API。

## 验收标准

- 入库命令执行成功。
- 数据库存在 1 条对应 Codex 长期测试账号。
- 输出不包含真实 token。
- 后端测试覆盖新增导入器、服务幂等行为、dry-run smoke 和真实 smoke 的脱敏结果处理。

## 风险

- 数据库长期持有真实测试凭证，需要限制 DB 访问和备份流转。
- 若加密 key 变化，旧 ciphertext 可能无法解密。

## 进度记录

- 2026-05-07：进入实现，先补文档和任务，再实现显式导入器与详测。
- 2026-05-07：用户补充允许使用真实 `auth.json` 测试，同时要求关注批量操作可信性与容错性；本任务将真实导入视为可重复批次，输出只保留脱敏证据。
- 2026-05-07：真实导入启动时发现数据库缺少 `distributed_key_secret_export_grant` 表，补充 Liquibase 0048 迁移以恢复生产 schema 校验。
- 2026-05-07：确认现有 Codex responses smoke 仅为 dry-run，继续补显式真实 smoke 路径。
- 2026-05-07：补充重复账号判定要求，避免同一 Codex 账号重复登录后因 `auth.json` 数据变化产生多个长期测试账号。
- 2026-05-07：真实标准 OpenAI `/v1/responses` smoke 返回 `401 invalid_request_error`；用户确认该凭证属于 Codex App API，下一步改查 Codex CLI 与参考项目保活实现。
- 2026-05-07：完成 Codex CLI 与参考项目核准：官方文章确认 ChatGPT 登录态 endpoint，Codex CLI issue/source 指向 `wham/usage` 轮询；参考项目显示 session headers、`prompt_cache_key`、SSE 和 `ChatGPT-Account-Id` 是保活与稳定反代的关键字段。
- 2026-05-07：参考 `cc-switch` 的 `gpt-5.4@low` Stream Check 与模型错误分类，决定把本地默认真实 smoke 从 `gpt-5-codex` 调整为 `gpt-5.4` + low reasoning，并为模型不适配增加单独 failureType。
- 2026-05-07：完成实现：默认模型改为 `gpt-5.4@low`，请求体实际发送 `gpt-5.4` 与 `reasoning.effort=low`；长期测试 dry-run 预览改为 Codex App API；模型不适配错误分类为 `MODEL_NOT_SUPPORTED`。
- 2026-05-07：真实验证通过：重复导入 `status=UPDATED`，`accountId=2`，`poolId=5`，`routeEligible=true`；`wham/usage` 保活 200；responses smoke 返回 200，requestId `9f7ef933bf6000b0-KIX`，responseId `resp_001b5803925e77b90169fc522c89708191a5c92e22468b9b1e`。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.OfficialAccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CodexAuthJsonParserTests" --tests "com.prodigalgal.xaigateway.admin.application.CodexLongTermTestImportServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CodexResponsesSmokeHttpClientTests"`：通过。
- `.\gradlew.bat bootRun --args="--spring.profiles.active=local --gateway.codex-test.import-auth-json-path=C:/Users/zzp84/Desktop/auth.json --gateway.codex-test.import-only=true --gateway.codex-test.live-smoke=true"`：通过。

## 结论

任务闭环。真实 Codex 测试账号已进入长期测试数据库基线，导入具备幂等更新、稳定账号重复判定、Codex App API 保活和真实 smoke 证据；输出与文档仅保留脱敏 ID、状态和 fingerprint。
