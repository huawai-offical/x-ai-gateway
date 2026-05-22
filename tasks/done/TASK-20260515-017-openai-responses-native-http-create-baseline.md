# TASK-20260515-017 OpenAI Responses Native HTTP Create 基线

状态：Done
优先级：Critical
类型：子任务切片
父任务：[TASK-20260514-018](../done/TASK-20260514-018-openai-responses-native-lifecycle.md)
上游来源：[TASK-20260514-013](../done/TASK-20260514-013-openai-chat-responses-native-parity.md)、[REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 背景

当前 `/v1/responses` ingress 能接收 Responses 请求，但 OpenAI Direct native runtime 的非流式执行仍复用 Spring AI `ChatCompletionRequest`。这会让 `include`、`previous_response_id`、`background`、`truncation`、Responses usage shape 和原生 response id/object 语义存在静默降级风险。完整 lifecycle 和 streaming SSE 透明转发范围较大，需要先把最核心的 OpenAI Direct create 非流式路径从 Chat fallback 中拆出来。

## 目标

- OpenAI Direct 且 ingress protocol 为 `RESPONSES` 的非流式执行，使用原生 `/v1/responses` HTTP JSON POST。
- 以上游原始 request body 为基础，只替换 route 后的 `model`，保留 `include`、`previous_response_id`、`store`、`background`、`metadata` 等 Responses 字段。
- 继续向 OpenAI Direct 下发 `OpenAI-Organization`、`OpenAI-Project`、`Idempotency-Key`。
- 将 OpenAI Responses JSON 响应映射回当前 canonical response，保证现有 controller/encoder 不破。
- 用本地 HTTP server 测试证明不再调用 ChatCompletionRequest，并验证 upstream path、headers、payload、usage 映射。

## 非目标

- 不在本切片实现 streaming raw SSE passthrough。
- 不在本切片实现 get/delete/cancel/input_items/count/compact lifecycle endpoint。
- 不改变 OpenAI-compatible provider 的现有 emulated fallback 行为。

## 输入

- `OpenAiNativeGatewayChatRuntime` 当前 native Chat fallback。
- `CanonicalRequest.providerExtensions()` 中保留的 Responses 原始 JSON body。
- `CanonicalRequestMetadata` 中的官方 OpenAI headers。

## 输出

- OpenAI Direct Responses native create HTTP executor 基线。
- Responses native JSON 响应到 canonical response 的安全映射。
- Runtime 单元测试覆盖 path/header/payload/usage。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntime.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntimeTests.java`
- `tasks/done/TASK-20260514-018-openai-responses-native-lifecycle.md`
- `docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md`

## 依赖

- `TASK-20260515-002` 官方 headers 下发基线。
- `TASK-20260515-014` Responses stream sequence 基线，后续 streaming passthrough 需要复用。

## 风险

- OpenAI Responses JSON shape 比 Chat Completion 更丰富，本切片只提取 text/tool_calls/usage 到 canonical response；原始对象完整透传仍留给 controller/native passthrough 后续切片。
- 非 2xx 错误需要避免泄露 key，本切片只返回截断后的上游错误摘要。

## 验收标准

- OpenAI Direct `RESPONSES` 非流式请求不调用 `chatCompletionEntity`。
- 真实发送路径为 `/v1/responses`，不是 `/v1/chat/completions`。
- 上游 payload 保留 Responses 专有字段并替换为路由解析后的模型名。
- 官方 OpenAI headers 被下发到上游。
- usage 中 `input_tokens`、`output_tokens`、`total_tokens`、cached/reasoning tokens 被映射。

## 测试边界

- 使用本地 `HttpServer` 模拟 OpenAI `/v1/responses`。
- 不访问真实 OpenAI 远端。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 实现结果

- `OpenAiNativeGatewayChatRuntime.execute` 在 `CanonicalIngressProtocol.RESPONSES` 且 provider 为 `OPENAI_DIRECT` 时改走原生 HTTP `/v1/responses` JSON POST。
- 上游 request body 以 `providerExtensions` 中保留的 Responses 原始 JSON 为基础，只覆盖路由解析后的 `model`，并显式设置 `stream=false`。
- OpenAI Direct 官方 headers `OpenAI-Organization`、`OpenAI-Project`、`Idempotency-Key` 继续下发。
- 原生 Responses JSON 响应映射为当前 `CanonicalResponse`：覆盖 `id`、`output_text`/`output[].content[].text`、reasoning summary、function call、usage 的 input/output/total/cached/reasoning token。
- OpenAI-compatible provider 仍保留原有 emulated fallback，本切片不扩大兼容承诺。

## 验证结果

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests"
```

结果：通过。

## 遗留边界

- 原始 Responses 对象完整透传、streaming raw SSE passthrough、`stream_options.include_obfuscation` 仍在 `TASK-20260514-018` 后续切片中。
- get/delete/cancel/input_items/count/compact lifecycle endpoints 仍未在本切片实现。
- 本切片不访问真实 OpenAI 远端，真实 smoke 由 `TASK-20260514-031` 后续资源族 smoke 处理。
