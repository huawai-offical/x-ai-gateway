# TASK-20260523-001 刷新模型失败健康状态写回

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260522-024-mimo-openai-key-refresh-401-diagnosis.md`

MiMo Key 1 刷新模型返回上游 401，但 `upstream_credential.last_error_*` 与 `cooldown_until` 没有写回，`request_log` 与 `ops_system_event` 也没有对应记录，导致控制台仍显示凭证正常。需要让管理端模型刷新失败变成可观测、可解释的状态。

## 目标

- 刷新模型失败时，将上游错误脱敏写回凭证 `last_error_code`、`last_error_message`、`last_error_at`。
- 根据错误类型决定是否进入短 cooldown，避免连续刷新打爆上游。
- 记录一条 `ops_system_event` 或等价管理端事件，包含 credentialId、providerType、baseUrl、HTTP status、traceId，不包含 secret。
- 前端凭证详情能够读取并展示最新失败状态。

## 非目标

- 不改变成功刷新模型的能力写入逻辑。
- 不输出或记录 API Key 明文、密文全文、Authorization header。
- 不对所有网关请求健康策略做大范围重构。
- 不把一次刷新失败永久禁用凭证。

## 上游来源

- `docs/requirements/REQ-20260522-024-mimo-openai-key-refresh-401-diagnosis.md`
- `tasks/done/TASK-20260522-025-mimo-openai-key-refresh-401-diagnosis-parent.md`

## 输入

- 上游模型发现异常。
- 当前凭证 ID、provider type、Base URL、site profile、protocol endpoint。
- 当前请求 traceId。

## 输出

- 失败时更新后的凭证健康字段。
- 一条可检索的管理端系统事件。
- 后端单元测试和前端状态展示回归。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/catalog/CredentialModelDiscoveryService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/CredentialAdminService.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/UpstreamCredentialEntity.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/OpsSystemEventEntity.java`
- `web/src/features/credentials/credentials-page.tsx`
- 后端与前端对应测试

## 依赖

- 现有凭证健康字段。
- 现有 traceId 过滤器和系统事件表。
- 前端凭证列表/详情已有 last error 字段展示能力。

## 风险

- 需要避免把短暂网络故障误判为永久凭证不可用。
- 错误消息必须脱敏，不能记录 Authorization header 或 secret。
- cooldown 时长需要保守，避免影响用户手动修复后立即重试。

## 验收标准

- [ ] 上游 401/403 刷新失败时写回 `last_error_code`、`last_error_message`、`last_error_at`。
- [ ] 刷新失败事件可在系统事件中按 credentialId 或 traceId 检索。
- [ ] 成功刷新仍清空 last error 并写入 `last_used_at`。
- [ ] 前端凭证页面能看到最近一次刷新失败状态。
- [ ] 单元测试覆盖成功、401、网络超时和脱敏边界。

## 测试边界

- 后端：mock WebClient 或 mock discovery 异常，不打真实上游。
- 前端：凭证页状态展示和刷新失败 toast。
- 不执行真实 MiMo Key 的失败重放。

## 关联文档

- `docs/requirements/REQ-20260522-024-mimo-openai-key-refresh-401-diagnosis.md`

## 关联任务

- `tasks/done/TASK-20260522-025-mimo-openai-key-refresh-401-diagnosis-parent.md`

## 当前状态

Backlog
