# REQ-20260506-017 AI IDE/CLI 官方账号导入与配额刷新

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-021 AI IDE/CLI 官方账号导入与配额刷新](../../tasks/done/TASK-20260506-021-ai-ide-account-import-quota-refresh.md)

## 背景

当前系统已有服务端 account pool、quota 字段、client metadata 与账号池运营基础。对照 `cockpit-tools-main` 的账号运营能力，仍缺 Codex、GitHub Copilot、Gemini CLI 等官方账号的云端导入、加密保存、订阅/配额识别、批量刷新和调度可见闭环。

## 目标

- 建立官方账号导入的最小后端闭环。
- 将 plan/subscription tier、quota window、reset time、remaining/used、quota error 写入可查询 metadata。
- 为调度器提供可解释的账号可用性、配额状态和下一次刷新时间。
- Token/secret 仅加密存储，审计与响应不回显明文。

## 范围

- Codex、GitHub Copilot、Gemini CLI 首批账号类型模型。
- JSON/OAuth/token 导入请求归一化。
- 配额刷新 service 与失败降级。
- Admin 后端 API 和单元测试。

## 非目标

- 不读取用户本机 profile 或 workspace。
- 不实现设备指纹、风控规避或切号注入。
- 不承诺非公开接口长期稳定。
- 不在仓库保存真实 token 或用户账号素材。

## 方案

1. 将 `TASK-021` 移入 `in-progress`。
2. 复用现有 `UpstreamCredentialEntity` 加密字段和 metadata，避免新增大迁移。
3. 新增官方账号导入/刷新 service，提供本地 deterministic quota probe。
4. 增加 Admin API：导入、刷新、配额摘要。
5. 增加测试覆盖成功导入、失败刷新、密文存储和调度可见状态。

## 风险

- 官方账号 provider API 可能变动；本轮不绑定非公开远程接口，只建立导入和刷新扩展点。
- secret 响应和审计必须保持脱敏。
- 真实 quota refresh 需要用户提供对应账号凭证后在本机 smoke。

## 验收标准

- 至少一个官方账号类型完成导入、刷新、配额查询和调度可见闭环。
- 刷新失败有可解释错误和下一次重试时间。
- Token/secret 加密存储，接口响应不包含明文。
- 文档和任务状态完成回写。

## 实现结果

- 新增 `OfficialAccountType`，覆盖 `CODEX`、`GITHUB_COPILOT`、`GEMINI_CLI`，并映射到 `CODEX_OAUTH`、`COPILOT_OAUTH`、`GEMINI_OAUTH`。
- 新增 `OfficialAccountAdminService`，提供官方账号导入、quota refresh 与 quota summary 查询。
- 新增 Admin API：
  - `POST /admin/accounts/official/import`
  - `POST /admin/accounts/{id}/official/quota-refresh`
  - `GET /admin/accounts/{id}/official/quota`
- 导入时复用 `UpstreamAccountEntity` 的 `accessTokenCiphertext` 与 `refreshTokenCiphertext`，token/secret 不进入响应、metadata 或 refresh result。
- quota refresh 写入 `refreshStatus`、`refreshFailureCount`、`nextRefreshAfter`、`cooldownUntil`、quota remaining、quota window、metadata `quota_status/quota_error` 与 `lastRefreshResultJson`。
- 新增 [official-account-quota-refresh](../official-account-quota-refresh.md) 契约文档。

## 测试/验证

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.OfficialAccountAdminServiceTests"`

## 遗留问题

- 本轮完成后端 Admin API 与调度事实源；复杂前端列表筛选和批量操作 UI 未扩展，可直接基于 `OfficialAccountQuotaResponse` 接入。
- 本轮 quota refresh 为 deterministic snapshot，不绑定非公开远程接口；真实远程探测需要按 provider adapter 后续替换。
