# TASK-20260515-023 OpenAI Responses Native Stream SSE 透明转发基线

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)  
上游来源：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)、[TASK-20260515-017](../done/TASK-20260515-017-openai-responses-native-http-create-baseline.md)、[TASK-20260515-022](../done/TASK-20260515-022-openai-responses-native-json-passthrough-baseline.md)

## 背景

`TASK-20260515-017` 已让 OpenAI Direct 非流式 `/v1/responses` 走原生 HTTP POST，`TASK-20260515-022` 又补齐了非流式原始 Responses JSON 透传。但当前 `stream=true` 仍经由本地 canonical stream encoder 重建事件，不能保留 OpenAI 官方 Responses streaming event 的完整原始 shape。官方文档明确：创建 Response 时设置 `stream=true` 后，服务端会用 server-sent events 持续发送 Response 生成过程中的事件；`stream_options.include_obfuscation` 也是 streaming 专用字段，默认包含 obfuscation delta。

## 目标

- 为 OpenAI Direct `/v1/responses` `stream=true` 建立 native upstream SSE 透明转发路径。
- 只在 OpenAI Direct + Responses native create + `stream=true` 时启用 raw SSE passthrough；其他 provider 或本地 fallback 继续使用现有 canonical stream encoder。
- 上游请求继续复用模型映射、认证、OpenAI 官方 headers 与请求体保真逻辑。
- 下游响应保持 `text/event-stream`，尽量原样输出上游 SSE 行、event、data、id、retry 与心跳空行。
- 测试覆盖本地 fake upstream SSE：确认请求走 `/v1/responses`、上游收到 `stream=true`、下游保留原始事件字段。

## 非目标

- 不解析或重写 upstream streaming data 内的 `model` 字段；本切片优先解决原始事件保真，避免破坏 unknown event schema。
- 不实现远端 retrieve/cancel/input_items passthrough。
- 不实现 `responses/input_tokens` 精确 tokenizer 或远端 compact/count passthrough。
- 不改变 OpenAI-compatible Generic、Grok、Azure、Claude、Gemini、Ollama 的 streaming 行为。

## 输入

- OpenAI Responses API create 文档。
- OpenAI Responses streaming events 文档。
- `OpenAiResponsesController`、`GatewayChatExecutionService`、`OpenAiNativeGatewayChatRuntime`、`OpenAiResponsesEncoder`。

## 输出

- OpenAI Direct Responses raw SSE passthrough 能力。
- controller/runtime 单元或切片测试。
- 公开兼容文档与 docs bundle 标注 raw SSE passthrough 边界。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntime.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/GatewayChatExecutionService.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesEncoder.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntimeTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `docs/public-api-compatibility.md`

## 依赖

- `TASK-20260515-017` 的 native OpenAI Responses HTTP create。
- `TASK-20260515-019` 的 stream options / obfuscation 兼容边界。
- `TASK-20260515-022` 的 raw payload 双轨思路。

## 风险

- raw SSE passthrough 如果强行解析重写 data，容易丢失官方新增 event 字段；本切片默认不解析 data。
- 如果复用既有同步 `RestClient` 路径读取整个流，可能破坏流式实时性；实现需要保留按行输出或以流式 response body 逐段写出。
- gateway 当前 lifecycle/observability 对 canonical stream 的 usage 聚合可能无法从 raw SSE 中完整提取，本切片需在文档中明确 raw passthrough 的观测边界。

## 验收标准

- OpenAI Direct + `stream=true` + Responses create 可以直接返回上游 SSE 原始事件。
- fake upstream 的未知字段、event name、sequence_number、obfuscation 与空行边界不会被 canonical encoder 重建或丢失。
- 非 OpenAI Direct 或无法 native passthrough 的路径继续使用既有 canonical stream。
- Targeted tests 通过，并更新父任务、报告和任务索引。

## 测试边界

- 不访问真实 OpenAI 远端。
- 使用本地 HTTP server 模拟 upstream `text/event-stream`。
- 重点验证 raw event passthrough、header/body 下发、fallback 不回归。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
- OpenAI Responses create: https://developers.openai.com/api/reference/resources/responses/methods/create
- OpenAI Responses streaming events: https://platform.openai.com/docs/api-reference/responses-streaming

## 实现结果

- `OpenAiNativeGatewayChatRuntime.executeStream` 在 OpenAI Direct + Responses ingress 下改走原生 `/v1/responses` SSE HTTP 请求，并向上游下发 `stream=true`、resolved model、OpenAI 官方 headers 与 `Accept: text/event-stream`。
- `CanonicalStreamEvent` 增加 `RAW_SSE` 事件与 `rawSsePayload` 承载位，旧构造器保持兼容。
- `OpenAiResponsesEncoder.encodeStream` 会在首个事件为 `RAW_SSE` 时直接输出上游 SSE 行，不再注入本地 synthetic `response.created/output_item.added` 等 canonical prelude。
- `GatewayChatExecutionService` 的 stream 首字节判断已识别 raw SSE payload，并修正 `enrichResponse` 保留 `rawResponse`，避免非流式 raw JSON 在 service 层丢失。
- 公开 docs bundle、OpenAPI snapshot 描述与兼容文档已标注 OpenAI Direct raw SSE passthrough 与 canonical fallback 边界。

## 验证情况

- `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`

## 遗留问题

- 本切片不解析或改写 raw SSE data 内部字段；因此 stream data 内的 upstream `model` 保持上游原始值。
- 远端 retrieve/cancel/input_items passthrough 已由 [TASK-20260516-001](TASK-20260516-001-openai-responses-remote-lifecycle-passthrough-baseline.md) 闭环；`responses/input_tokens` native passthrough 已由 [TASK-20260516-002](TASK-20260516-002-openai-responses-input-tokens-native-passthrough.md) 闭环；`responses/compact` native passthrough 已由 [TASK-20260516-003](TASK-20260516-003-openai-responses-compact-native-passthrough.md) 闭环。
