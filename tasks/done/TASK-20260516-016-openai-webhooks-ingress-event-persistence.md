# TASK-20260516-016 OpenAI Webhooks 接收入口与事件落库

状态：Done
优先级：High
类型：子任务
父任务：[TASK-20260514-019](../backlog/TASK-20260514-019-openai-conversations-webhooks-tools.md)
上游来源：[REQ-20260516-016](../../docs/requirements/REQ-20260516-016-openai-webhooks-ingress-event-persistence.md)

## 背景

`TASK-20260515-013` 已闭环 OpenAI webhook signature/replay verifier，但 `TASK-20260514-019` 仍缺实际 Webhooks controller 与 event persistence。官方 Webhooks 文档强调 raw body 验签、快速返回 2xx，并用 `webhook-id` 处理重复投递。

## 目标

- 新增 `POST /v1/webhooks/openai`。
- 复用 `OpenAiWebhookSignatureVerifier`。
- 保存合法 event 到 `gateway_async_resource`。
- 对重复 delivery 与重复 event id 做幂等响应。
- 同步 public docs、OpenAPI、provider catalog、reports 和 task index。

## 非目标

- 不实现 dashboard webhook endpoint 管理 API。
- 不执行 event data 的业务副作用。
- 不新增后台 UI。

## 输入

- OpenAI Webhooks 官方文档。
- `OpenAiWebhookSignatureVerifier`。
- `gateway_async_resource` 持久层。

## 输出

- `OpenAiWebhooksController`
- `OpenAiWebhookEventService`
- `GatewayAsyncResourceType.WEBHOOK_EVENT`
- controller/service/docs/provider catalog/OpenAPI 测试。

## 影响范围

- OpenAI ingress、async resource、public docs、provider catalog、reports、tasks。

## 依赖

- [TASK-20260515-013 OpenAI Webhook Signature 与 Replay 防护基线](../done/TASK-20260515-013-openai-webhook-signature-replay-baseline.md)
- [TASK-20260516-015 OpenAI Conversations 本地 Lifecycle](../done/TASK-20260516-015-openai-conversations-local-lifecycle.md)

## 风险

- Raw body 处理错误会导致验签失败。
- Duplicate delivery 与 duplicate event id 需要分别兜底。
- Webhook secret 只能走配置，不能进入持久化内容。

## 验收标准

- 合法 `response.completed` 样例可验签、落库并返回 `received=true`。
- 重复 delivery 返回 `duplicate=true` 且不二次保存。
- 重复 event id 返回 `duplicate=true` 且不二次保存。
- 非法签名返回 OpenAI-style `invalid_request_error`。
- docs/catalog/OpenAPI 均公开该能力边界。

## 测试边界

- `OpenAiWebhookEventServiceTests`
- `OpenAiWebhooksControllerTests`
- `OpenAiWebhookSignatureVerifierTests`
- `PublicDocsBundleServiceTests`
- `ProviderCatalogLoaderTests`
- `PublicOpenApiSnapshotTests`

## 关联文档

- [REQ-20260516-016](../../docs/requirements/REQ-20260516-016-openai-webhooks-ingress-event-persistence.md)
- [OpenAI API 完整兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)
- [OpenAI API 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
- [TASK-20260514-019](../backlog/TASK-20260514-019-openai-conversations-webhooks-tools.md)

## 当前状态

- 2026-05-16：任务创建，进入实现。
- 2026-05-16：实现、公开契约与聚焦回归已完成，移动到 `tasks/done/`。

## 实现结果

- 新增 `POST /v1/webhooks/openai`，接收 raw JSON body 与 Standard Webhooks headers。
- 复用 `OpenAiWebhookSignatureVerifier` 完成 timestamp、signature 和 replay marker 校验；缺失 header 统一返回 OpenAI-style 400。
- 新增 `OpenAiWebhookEventService`，将合法 event 落为 `WEBHOOK_EVENT`，记录 raw payload、event JSON 与 webhook metadata。
- 支持重复 delivery 与重复 event id 双重幂等，重复请求返回 `duplicate=true` 且不二次保存。
- 已同步 `docs/openai-webhooks-ingress-event-persistence.md`、`docs/public-api-compatibility.md`、public docs bundle、OpenAPI snapshot、provider catalog、审计报告、父任务和索引。

## 验证记录

2026-05-16 已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiWebhookEventServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiWebhooksControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiWebhookSignatureVerifierTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.infra.config.web.OpenAiProtocolPathMatcherTests"
```

## 遗留问题

- Webhooks 调试视图未纳入本任务闭环；后续如需要运营查询，可单独拆 UI/查询任务。
- Event data 业务副作用未实现，避免在 ingress 基线阶段引入不可审计 side effect。
