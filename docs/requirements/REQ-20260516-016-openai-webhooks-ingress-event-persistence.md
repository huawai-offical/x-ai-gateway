# REQ-20260516-016 OpenAI Webhooks 接收入口与事件落库

状态：Done
日期：2026-05-16
来源任务：[TASK-20260514-019](../../tasks/backlog/TASK-20260514-019-openai-conversations-webhooks-tools.md)

## 背景

OpenAI Webhooks 官方文档要求服务端接收 HTTP POST、使用 Standard Webhooks headers 验签、快速返回 2xx，并建议用 `webhook-id` 作为幂等键处理重复投递。当前项目已在 `TASK-20260515-013` 完成 `OpenAiWebhookSignatureVerifier`，但还没有实际 `/v1/webhooks/...` 接收入口和事件落库证据。

## 目标

- 增加 `POST /v1/webhooks/openai` 接收入口。
- 复用 `OpenAiWebhookSignatureVerifier` 校验 `webhook-id`、`webhook-timestamp`、`webhook-signature`。
- 将合法 OpenAI event 保存为本地 `gateway_async_resource`，形成可追踪证据。
- 对重复 delivery 或重复 event id 返回幂等 `received=true, duplicate=true`，不重复落库。
- 同步 public docs、provider catalog、报告和任务体系。

## 非目标

- 不实现 OpenAI Dashboard webhook endpoint 管理 API。
- 不执行 event data 的业务副作用，例如自动 retrieve response 或触发下游 worker。
- 不新增后台 UI；本轮只完成可测试的接收与持久化基线。

## 方案

1. `GatewayAsyncResourceType` 新增 `WEBHOOK_EVENT`。
2. 新增 `OpenAiWebhooksController`：
   - `POST /v1/webhooks/openai`
   - 接收 raw body，不能先转 JSON 再验签。
   - 读取 Standard Webhooks headers 后调用 verifier。
3. 新增 `OpenAiWebhookEventService`：
   - 解析 event JSON。
   - 以 event `id` 为 resource key；缺失时使用 payload hash 生成本地 key。
   - `distributedKeyId=0` 表示系统级 inbound OpenAI webhook。
   - `metadata_json` 保存 webhook id、timestamp、event type、duplicate marker。
4. Duplicate 判断：
   - verifier 返回 `duplicateDelivery=true` 时不落库。
   - resource key 已存在时不落库。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceType.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/GatewayAsyncResourceRepository.java`
- public docs、OpenAPI snapshot、provider catalog、OpenAI coverage reports、task index。

## 风险

- 若 raw body 经过 JSON 反序列化再验签，会导致签名不一致；controller 必须接收 raw string。
- 重复投递既可能复用同一个 `webhook-id`，也可能同一个 event id 以新 delivery 发送；两层都要兜底。
- Webhook secret 不能写入文档或日志，测试只使用固定假 secret。

## 验收标准

- 合法签名的 event 返回 200，落库为 `WEBHOOK_EVENT`。
- 同一 `webhook-id` 重复投递返回 duplicate，不再次保存。
- 同一 event id 不同 `webhook-id` 返回 duplicate，不再次保存。
- 非法签名、缺失 headers、非法 JSON 返回 OpenAI-style error。
- Public docs 与 provider catalog 增加 `openai.webhooks-ingress-event-persistence`。

## 测试边界

- `OpenAiWebhookEventServiceTests`
- `OpenAiWebhooksControllerTests`
- `OpenAiWebhookSignatureVerifierTests`
- `PublicDocsBundleServiceTests`
- `ProviderCatalogLoaderTests`
- `PublicOpenApiSnapshotTests`

## 实现结果

- 新增 `OpenAiWebhooksController`，公开 `POST /v1/webhooks/openai`，以 raw body 调用 `OpenAiWebhookSignatureVerifier`。
- 新增 `OpenAiWebhookEventService`，将合法 OpenAI event 保存为 `GatewayAsyncResourceType.WEBHOOK_EVENT`，并用 `distributedKeyId=0` 标记系统级 inbound webhook。
- Duplicate 覆盖两层：`webhook-id` replay marker 与 event `id`/fallback hash resource key；重复时返回 `received=true`、`duplicate=true`，不二次保存。
- 缺失 headers、非法签名与非法 JSON 进入 OpenAI-style error envelope。
- Public docs bundle、OpenAPI snapshot、provider catalog、兼容性文档和审计报告已同步 `openai.webhooks-ingress-event-persistence`。

## 验证记录

2026-05-16 已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiWebhookEventServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiWebhooksControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiWebhookSignatureVerifierTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.infra.config.web.OpenAiProtocolPathMatcherTests"
```

## 遗留问题

- 本轮不实现 OpenAI Dashboard webhook endpoint 管理 API。
- 本轮不执行 event data 的业务副作用。
- 如后续运营排查需要，可基于 `WEBHOOK_EVENT` 与 request log 补 portal/admin 调试视图。
