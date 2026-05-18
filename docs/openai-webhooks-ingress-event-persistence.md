# OpenAI Webhooks 接收入口与事件落库

关联需求：[REQ-20260516-016](requirements/REQ-20260516-016-openai-webhooks-ingress-event-persistence.md)
关联任务：[TASK-20260516-016](../tasks/done/TASK-20260516-016-openai-webhooks-ingress-event-persistence.md)

## 接收入口

```text
POST /v1/webhooks/openai
```

该入口用于接收 OpenAI Webhooks delivery。请求必须保留原始 JSON body，并携带 Standard Webhooks headers：

- `webhook-id`
- `webhook-timestamp`
- `webhook-signature`

验签由 `OpenAiWebhookSignatureVerifier` 完成。签名 payload 为：

```text
webhook-id.webhook-timestamp.raw_body
```

`gateway.openai.webhook.secret` 可配置默认 `whsec_` secret 或 raw secret。timestamp tolerance 默认 5 分钟，`webhook-id` replay marker 默认保留 24 小时。

## 事件落库

合法 event 会保存到 `gateway_async_resource`：

- `resourceType=WEBHOOK_EVENT`
- `distributedKeyId=0`，表示系统级 inbound OpenAI webhook。
- `resourceKey` 优先使用 event `id`；payload 缺少 `id` 时使用 `evt_gateway_` 加 payload hash。
- `requestModel=openai.webhook`
- `status=received`
- `requestPayloadJson` 保存 raw body。
- `responsePayloadJson` 保存 event JSON。
- `metadataJson` 保存 `webhook_id`、`webhook_timestamp`、`event_type`、`source=openai` 与 `received_at`。

## 幂等行为

入口有两层幂等兜底：

- 同一个 `webhook-id` 重复投递时，verifier 返回 `duplicateDelivery=true`，接口返回 `duplicate=true`，不再次保存。
- 不同 `webhook-id` 但 event `id` 已存在时，接口同样返回 `duplicate=true`，不再次保存。

成功响应示例：

```json
{
  "object": "webhook.delivery",
  "id": "wh_123",
  "event_id": "evt_123",
  "type": "response.completed",
  "received": true,
  "duplicate": false,
  "created_at": 1778889600
}
```

## 当前边界

- 本轮不实现 OpenAI Dashboard webhook endpoint 管理 API。
- 本轮不执行 event data 的业务副作用，例如自动 retrieve response 或触发下游 worker。
- 本轮不新增后台 UI；后续如果需要人工排查入口，可以在 request log / async resource 查询之上再补专门调试视图。

## 验证

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiWebhookEventServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiWebhooksControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiWebhookSignatureVerifierTests"
```
