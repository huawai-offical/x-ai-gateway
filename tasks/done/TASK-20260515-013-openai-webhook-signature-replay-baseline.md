# TASK-20260515-013 OpenAI Webhook Signature 与 Replay 防护基线

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260514-030](../backlog/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)  
上游来源：[TASK-20260514-019](../backlog/TASK-20260514-019-openai-conversations-webhooks-tools.md)

## 背景

OpenAI webhook 按 Standard Webhooks 规范投递，核心 headers 为 `webhook-id`、`webhook-timestamp` 与 `webhook-signature`，签名内容为 `webhook-id.webhook-timestamp.raw_body`。当前项目尚无可复用的 OpenAI webhook 签名校验与重复投递防护模块，后续 Webhooks controller 如果直接接入业务处理会留下安全风险。

## 目标

- 实现可复用的 OpenAI webhook signature verifier。
- 支持 Standard Webhooks `whsec_` base64 secret 与本地 raw secret。
- 校验 `webhook-id`、`webhook-timestamp`、`webhook-signature` 三个 header。
- 校验 timestamp tolerance，默认 5 分钟，可配置。
- 支持 `webhook-signature` 的多签名空格分隔格式，至少校验 `v1,base64`。
- 使用 `RateLimitStore` 对 `webhook-id` 做 replay marker，默认保留 24 小时，可配置。
- 返回 duplicate delivery 标记，便于后续 controller 做幂等 2xx 响应。

## 非目标

- 不在本轮实现 OpenAI Webhooks controller。
- 不落库保存 webhook event 或投递审计。
- 不处理 Webhook endpoint CRUD；这属于 `TASK-20260514-019`。

## 输入

- OpenAI Webhooks guide。
- Standard Webhooks spec。
- 当前 `RateLimitStore`。

## 输出

- `OpenAiWebhookSignatureVerifier` service。
- 单元测试覆盖有效签名、重复投递、错误签名、过期 timestamp、raw secret。
- public docs bundle 与 Markdown 增加 webhook signature/replay 说明。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiWebhookSignatureVerifier.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiWebhookSignatureVerifierTests.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java`
- `docs/public-api-compatibility.md`
- `tasks/index.md`

## 依赖

- `RateLimitStore` 已存在 Redis-backed 实现，可复用 TTL marker。
- `TASK-20260514-019` 后续 controller 需要接入本 verifier。

## 风险

- 如果 Redis 不可用，`RedisRateLimitStore` 当前会降级返回本次 increment 数值，多实例 replay 防护会弱化；本任务先建立接口和测试基线，后续可在 Runtime Store 层补强失败策略。
- 使用 raw string payload 校验时，未来 controller 必须传入原始 body，不能先格式化 JSON 后再验签。

## 验收标准

- 有效 `whsec_` secret 与 `v1` 签名可通过校验。
- 同一个 `webhook-id` 第二次校验返回 `duplicateDelivery=true`。
- 错误签名、缺 header、timestamp 超出 tolerance 都会拒绝。
- 文档说明 raw body、headers、secret 和 replay marker 语义。

## 测试边界

- 仅单元测试 verifier，不跑真实 OpenAI webhook。

## 关联文档

- [TASK-20260514-019](../backlog/TASK-20260514-019-openai-conversations-webhooks-tools.md)
- [TASK-20260514-030](../backlog/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)

## 实现结果

- 新增 `OpenAiWebhookSignatureVerifier`，按 Standard Webhooks 形态校验 `webhook-id`、`webhook-timestamp`、`webhook-signature`。
- 支持 `whsec_` base64 secret 与 raw string secret，签名内容固定为 `webhook-id.webhook-timestamp.raw_body`。
- 默认 timestamp tolerance 为 5 分钟，可通过 `gateway.openai.webhook.timestamp-tolerance` 调整。
- 默认 replay marker 保留 24 小时，可通过 `gateway.openai.webhook.replay-window` 调整。
- 复用 `RateLimitStore` 记录 `webhook-id`，第二次同 id 校验返回 `duplicateDelivery=true`，供后续 Webhooks controller 做幂等 2xx 处理。
- public docs bundle 与 `docs/public-api-compatibility.md` 已补充 webhook signature/replay 契约说明。

## 验证结果

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiWebhookSignatureVerifierTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"
```

覆盖项：

- 有效 `whsec_` secret 与 `v1` 签名。
- raw secret override 与多签名格式。
- 同一个 `webhook-id` 的 duplicate delivery 标记。
- 错误签名、缺失 header、过期 timestamp 的拒绝路径。
- public docs bundle 中的 `openai.webhook-signature-replay` conformance 项。

## 遗留问题

- Webhooks controller 与 event 落库仍归属 `TASK-20260514-019`，本任务只提供可复用 verifier。
- 后续 controller 必须传入原始 request body 做验签，不能先格式化 JSON。
- 如果 Redis 不可用，当前 replay marker 的多实例强一致性仍取决于 `RateLimitStore` 降级策略，后续可在 runtime store 层继续补强。
