# AI IDE/CLI 官方账号导入与配额刷新

日期：2026-05-06  
关联需求：[REQ-20260506-017 AI IDE/CLI 官方账号导入与配额刷新](requirements/REQ-20260506-017-official-account-import-quota-refresh.md)  
关联任务：[TASK-20260506-021 AI IDE/CLI 官方账号导入与配额刷新](../tasks/done/TASK-20260506-021-ai-ide-account-import-quota-refresh.md)

## 背景

Codex、GitHub Copilot、Gemini CLI 这类 AI IDE/CLI 官方账号需要云端统一导入、加密保存和配额运营面。系统已有 `UpstreamAccountEntity` 的 token 密文字段、quota 字段和 refresh 状态字段，本轮在这些事实源上补齐官方账号专用 API。

## 支持账号类型

| accountType | providerType | 默认 client family | 默认模型 |
| --- | --- | --- | --- |
| `CODEX` | `CODEX_OAUTH` | `CODEX` | `gpt-4.1`、`o4-mini` |
| `GITHUB_COPILOT` | `COPILOT_OAUTH` | `GITHUB_COPILOT` | `gpt-4.1`、`claude-3.7-sonnet` |
| `GEMINI_CLI` | `GEMINI_OAUTH` | `GEMINI_CLI` | `gemini-2.5-pro`、`gemini-2.5-flash` |

## Admin API

### 导入官方账号

`POST /admin/accounts/official/import`

核心请求字段：

| 字段 | 说明 |
| --- | --- |
| `accountType` | `CODEX`、`GITHUB_COPILOT`、`GEMINI_CLI`。 |
| `poolId` | 可选账号池；账号池 `providerType` 必须与账号类型匹配。 |
| `accessToken` | 必填，服务端只保存密文。 |
| `refreshToken` | 可选，服务端只保存密文。 |
| `metadataJson` | 可选，敏感 key 会写成 `***`。 |
| `supportedModels` | 可选；为空时使用账号池模型或账号类型默认模型。 |
| `planTier`、`subscriptionTier` | 可选订阅/套餐信息。 |
| `quotaWindowSeconds`、`quotaRemainingTokens`、`quotaRemainingRequests`、`quotaResetAt` | 可选配额快照。 |
| `refreshQuotaAfterImport` | 默认 `true`，导入后写入本地 deterministic quota snapshot。 |

### 刷新官方账号配额

`POST /admin/accounts/{id}/official/quota-refresh`

核心请求字段：

| 字段 | 说明 |
| --- | --- |
| `planTier`、`subscriptionTier` | 可更新套餐信息。 |
| `quotaWindowSeconds`、`quotaRemainingTokens`、`quotaRemainingRequests`、`quotaResetAt` | 配额刷新结果。 |
| `quotaError`、`forceFailure` | 写入可解释失败、`nextRefreshAfter` 和冷却时间。 |

### 查询官方账号配额

`GET /admin/accounts/{id}/official/quota`

响应 `OfficialAccountQuotaResponse` 会返回账号类型、provider type、模型列表、plan/subscription tier、quota window、reset time、remaining、last refresh、next refresh、健康状态、`routeEligible` 与 `routeBlockReason`。

## 安全边界

- `accessToken` 与 `refreshToken` 仅写入 `UpstreamAccountEntity` 密文字段，不进入响应、metadata 或 refresh result。
- `metadataJson` 中包含 `token`、`secret`、`api_key`、`authorization`、`cookie` 的 key 会脱敏为 `***`。
- 本轮不读取用户本机 profile、workspace、浏览器 cookie 或 IDE 配置。
- 本轮的 quota refresh 是后端可验证的 deterministic snapshot，不绑定非公开远程接口；真实远程探测可在后续 adapter 中替换。

## 调度可见字段

| 字段 | 来源 | 用途 |
| --- | --- | --- |
| `refreshStatus` | `UpstreamAccountEntity.refreshStatus` | 标识 `IMPORTED`、`QUOTA_READY`、`QUOTA_FAILED`。 |
| `nextRefreshAfter` | `UpstreamAccountEntity.nextRefreshAfter` | 调度器下一次刷新时间。 |
| `cooldownUntil` | `UpstreamAccountEntity.cooldownUntil` | 失败冷却窗口。 |
| `quotaRemainingTokens`、`quotaRemainingRequests` | `UpstreamAccountEntity` quota 字段 | 路由可用性判断。 |
| `quota_status`、`quota_error` | metadata | 管理端展示和故障解释。 |
| `lastRefreshResultJson` | `UpstreamAccountEntity.lastRefreshResultJson` | 审计与排障摘要。 |
