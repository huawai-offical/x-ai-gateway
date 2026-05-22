# TASK-20260515-022 OpenAI Responses Native JSON 原始对象透传基线

状态：Done
优先级：Critical
类型：子任务切片
父任务：[TASK-20260514-018](../done/TASK-20260514-018-openai-responses-native-lifecycle.md)
上游来源：[TASK-20260514-018](../done/TASK-20260514-018-openai-responses-native-lifecycle.md)、[TASK-20260515-017](../done/TASK-20260515-017-openai-responses-native-http-create-baseline.md)

## 背景

`TASK-20260515-017` 已让 OpenAI Direct 非流式 `/v1/responses` 走原生 HTTP POST，但 runtime 目前会把上游 Responses JSON 抽取成 `CanonicalResponse`，controller 再重建一个简化 Responses 对象。这样会丢失上游原始字段，例如 richer `output` item、annotations、incomplete details、tool metadata、provider-specific top-level fields 等。父任务仍明确要求“原始 Responses 对象完整透传到 public API 的双轨输出”。

## 目标

- `CanonicalResponse` 增加可选 raw upstream response 承载位，同时保持旧构造器兼容。
- `OpenAiNativeGatewayChatRuntime` 在 OpenAI Direct Responses native create 成功后，把原始 JSON 保存到 `CanonicalResponse`。
- `OpenAiResponsesController` 对 raw Responses JSON 优先原样返回，只重写 `model` 为 public model；没有 raw payload 时继续使用 canonical encoder。
- `store=true` 与 `Idempotency-Key` 记忆使用同一最终 JSON payload，避免返回对象与存储对象漂移。

## 非目标

- 不实现 streaming raw SSE 透明转发。
- 不实现远端 retrieve/cancel/input_items passthrough。
- 不改变 Chat Completions、Claude、Gemini、Ollama 的响应编码。

## 输入

- OpenAI Direct `/v1/responses` native HTTP create 返回体。
- `CanonicalResponse`、`OpenAiResponsesEncoder`、`OpenAiResponsesController`。

## 输出

- 原始 Responses JSON 双轨承载与 controller 输出。
- runtime local HTTP server test。
- controller raw payload test。
- 公开文档更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/canonical/CanonicalResponse.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntime.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesEncoder.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntimeTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `docs/public-api-compatibility.md`

## 依赖

- `TASK-20260515-017` OpenAI Direct Responses native HTTP create。

## 风险

- raw payload 如果直接返回 resolved model，会泄露内部模型映射；本切片必须把 raw payload 中的 `model` 重写为 public model。
- canonical response 构造器被多处使用，新增字段必须保留旧签名，避免大面积调用点 churn。

## 验收标准

- OpenAI Direct native runtime 返回的 `CanonicalResponse.rawResponse` 包含上游原始 Responses JSON。
- Controller 对 raw payload 优先返回原始 object，并保留 nested output annotations 等 canonical encoder 不认识的字段。
- 返回和 `store=true`/idempotency remember 使用同一最终 JSON。
- 既有 canonical fallback 响应不变。

## 测试边界

- `OpenAiNativeGatewayChatRuntimeTests`
- `OpenAiResponsesControllerTests`
- 不访问真实 OpenAI 远端。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 实现结果

- `CanonicalResponse` 新增可选 `rawResponse` 字段，并保留旧构造器兼容现有调用点。
- `OpenAiNativeGatewayChatRuntime` 在 OpenAI Direct Responses native create 成功后保留上游原始 JSON。
- `OpenAiResponsesEncoder.encodeJson` 优先返回 raw Responses JSON，并将 `model` 重写为 public model；无 raw payload 时回退 canonical encoder。
- `OpenAiResponsesController` 的普通返回、`store=true` 和 `Idempotency-Key` remember 使用同一最终 JSON payload。
- 公开 docs bundle 与兼容文档已标注非流式 raw JSON passthrough 边界。
- 2026-05-16 补充：`GatewayChatExecutionService.enrichResponse` 已保留 `rawResponse`，避免 runtime 获取的 raw payload 在 service enrich 阶段丢失。

## 验证情况

- `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`
- 2026-05-16 补充：`.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests"`

## 遗留问题

- 本切片不实现 streaming raw SSE 透明转发。
- 远端 retrieve/cancel/input_items passthrough 已由 [TASK-20260516-001](TASK-20260516-001-openai-responses-remote-lifecycle-passthrough-baseline.md) 闭环；`responses/input_tokens` native passthrough 已由 [TASK-20260516-002](TASK-20260516-002-openai-responses-input-tokens-native-passthrough.md) 闭环；`responses/compact` native passthrough 已由 [TASK-20260516-003](TASK-20260516-003-openai-responses-compact-native-passthrough.md) 闭环。
