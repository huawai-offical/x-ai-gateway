# TASK-20260507-001 Codex 官方账号真实适配、配额刷新与反代 Smoke

状态：Done  
优先级：High  
排期：P0-04  
来源：[REP-20260507 Codex 账户反代与 UI/UX 深度差距分析](../../docs/reports/REP-20260507-codex-proxy-uiux-gap-analysis.md)
关联需求：[REQ-20260507-003 第二批最高优先级任务闭环设计](../../docs/requirements/REQ-20260507-003-next3-priority-closure-design.md)

## 背景

当前 `OfficialAccountAdminService` 已支持 `CODEX` 官方账号导入与 deterministic quota snapshot，但这仍是后端可验证的本地闭环，不是面向真实 Codex 账号的生产反代 adapter。对照 `cockpit-tools-main` 的 Codex 计划识别、配额刷新，以及 `cli_proxy-master` 的 Codex endpoint smoke，当前还缺真实连通、真实配额解释和反代 smoke。

## 目标

- 建立 Codex 官方账号真实 adapter 边界和 token 使用方式。
- 支持真实 Codex 账号配额/plan 刷新结果写入现有 quota 字段。
- 为 `CODEX_OAUTH` 账号提供 `/v1/responses` 反代 smoke。
- 在失败时写入可解释错误、冷却时间和路由阻断原因。

## 详细设计

- 新增 `OfficialCodexAccountAdapter` 或等价服务，输入为已加密保存的 access/refresh token，输出 plan、subscription、quota、resetAt、可用模型和错误摘要。
- 将 adapter 接入 `OfficialAccountAdminService.refreshQuota` 的真实刷新路径，保留 deterministic snapshot 作为本地测试 fallback。
- smoke 使用网关内部执行路径，构造最小 `/v1/responses` 请求，校验 status、SSE/JSON 响应、usage 解析和 route decision。
- refresh 失败时更新 `refreshStatus=QUOTA_FAILED`、`nextRefreshAfter`、`cooldownUntil`、`lastRefreshResultJson`，并保证 token 不进入日志、metadata 或响应。

## 验收标准

- 真实 Codex 官方账号可执行手动配额刷新。
- 成功刷新能更新 plan、subscription、remaining、resetAt、nextRefreshAfter。
- 失败刷新有冷却和可解释 route block reason。
- Codex 账号可跑 `/v1/responses` smoke，并沉淀测试或 smoke harness。
- 文档、需求和任务状态在实现时完整回写。

## 风险

- Codex 官方接口可能变化，adapter 必须隔离在 provider/account 层，不能污染通用 OpenAI-compatible 路径。
- 真实账号 smoke 需要用户提供合法测试账号和额度；无真实凭证时只能跑 contract/local fallback。
- 真实 `auth.json` 只能做本机只读测试输入，token 不得进入日志、文档、测试快照或仓库差异。

## 进度记录

- 2026-05-07：进入实现批次，补充真实 `auth.json` 安全边界，开始设计 parser、脱敏摘要、refresh contract 与 smoke harness。
- 2026-05-07：完成 `CodexAuthJsonParser`、官方账号 raw `auth.json` metadata 导入、脱敏摘要、空请求配额刷新 snapshot 和 `/admin/accounts/{id}/official/codex/responses-smoke` dry-run smoke。

## 实现结果

- 支持真实 Codex `auth.json` 的 `tokens.access_token`、`tokens.refresh_token`、`tokens.account_id`、`OPENAI_API_KEY` fallback 解析。
- 保存 metadata 时递归脱敏 token/secret/key/cookie 字段，响应只暴露 fingerprint、presence 和 route 可用性。
- 失败刷新继续写入 `QUOTA_FAILED`、`nextRefreshAfter`、`cooldownUntil` 和 `ACCOUNT_UNHEALTHY` route block reason。
- dry-run smoke 输出 `/v1/responses` method/path/model/header/body 预览，不返回真实 token。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.CodexAuthJsonParserTests" --tests "com.prodigalgal.xaigateway.admin.application.OfficialAccountAdminServiceTests"` 通过。
- `.\gradlew.bat test` 通过。
- 本轮新增/修改文件未发现真实 `AIzaSy`、`ya29.`、超长 token 或真实 OpenAI/Codex key 形态。

## 遗留问题

- 已闭环 dry-run smoke 与真实凭证解析；真实联网 smoke 需后续在受控环境显式启用，避免误消耗用户额度。

## 2026-05-21 口径补充

- 本任务保留的是官方账号导入、配额刷新与 `/v1/responses` smoke 的后端能力基线。
- 它不代表当前仍保留独立 `官方账号运行态` 控制台页面；控制台产品面已进入收敛/下线范围。
