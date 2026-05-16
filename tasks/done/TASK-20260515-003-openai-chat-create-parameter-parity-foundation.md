# TASK-20260515-003 OpenAI Chat Completions Create 参数保真基线

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260514-017](../backlog/TASK-20260514-017-openai-chat-completions-full-parity.md)  
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[REP-20260514](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 背景

官方 Chat Completions create 参数面包含 `store`、`metadata`、`frequency_penalty`、`logit_bias`、`logprobs`、`top_logprobs`、`max_completion_tokens`、`n`、`presence_penalty`、`seed`、`service_tier`、`stop`、`top_p`、`user`、`verbosity`、`safety_identifier` 等。此前 `OpenAiChatCompletionRequest` 只覆盖核心字段，未知字段可能无法进入 canonical request，也无法由 native runtime 保真下发。

## 目标

- 扩展 `OpenAiChatCompletionRequest`，显式接收一批官方 create 常用参数。
- `OpenAiChatCompletionRequestMapper` 将新增参数写入 `providerExtensions`，避免在 canonical 层丢失。
- `OpenAiNativeGatewayChatRuntime` 将可由 Spring AI `ChatCompletionRequest` 原生承接的字段下发到上游。
- 对仍需复杂类型或 stored lifecycle 支持的字段保持后续任务边界，不在本轮虚假声明全量完成。

## 非目标

- 不实现 stored Chat Completion 的 list/get/update/delete/messages。
- 不实现复杂对象的完全类型化转换；`response_format` 已由后续 `TASK-20260515-006` 完成强类型映射，`modalities/audio/web_search_options` 已由后续 `TASK-20260515-007` 完成强类型映射。
- 不承诺所有 OpenAI-compatible provider 都支持这些参数。

## 输入

- 官方 Chat Completions API Reference。
- `OpenAiChatCompletionRequest`
- `OpenAiChatCompletionRequestMapper`
- `OpenAiNativeGatewayChatRuntime`
- `OpenAiChatCompletionsControllerTests`
- `OpenAiNativeGatewayChatRuntimeTests`

## 输出

- `OpenAiChatCompletionRequest` 显式新增 Chat create 常用参数，并保留旧构造器兼容 `OllamaNativeController` 内部适配调用。
- `OpenAiChatCompletionRequestMapper` 将新增字段写入 `providerExtensions`。
- `OpenAiNativeGatewayChatRuntime` 原生映射 `store`、`metadata`、`frequency_penalty`、`logit_bias`、`logprobs/top_logprobs`、`max_completion_tokens`、`n`、`presence_penalty`、`seed`、`service_tier`、`stop`、`top_p`、`user`、`verbosity`、`safety_identifier`。
- 复杂对象 `stream_options`、`prediction` 仍以 `extraBody` flatten 方式保真下发；`functions/function_call`、`response_format`、`modalities/audio/web_search_options` 已分别由后续切片完成语义化转换。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionRequest.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionRequestMapper.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntime.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntimeTests.java`
- `tasks/index.md`

## 依赖

- `TASK-20260514-030` 已完成 error/request id 与官方 headers 两个基线切片。
- Spring AI `OpenAiApi.ChatCompletionRequest` 当前支持的原生字段。

## 风险

- `max_tokens` 与 `max_completion_tokens` 并存时需要保持各自语义，不能互相覆盖。
- `stop` 在官方 API 支持 string 或 array，本轮已归一为 runtime 可下发的 list。
- 复杂对象如果半转换容易产生错误 schema，本轮只做明确保真或后续拆分。

## 验收标准

- Controller 能接收新增 Chat create 参数并传入 `CanonicalRequest.providerExtensions`。
- Runtime buildRequest 能映射 `store`、`metadata`、`frequency_penalty`、`logit_bias`、`logprobs/top_logprobs`、`max_completion_tokens`、`n`、`presence_penalty`、`seed`、`service_tier`、`stop`、`top_p`、`user`、`verbosity`、`safety_identifier`。
- 既有 minimal Chat、stream、错误 envelope 测试不回归。
- 定向测试通过。

## 测试边界

- 更新 `OpenAiChatCompletionsControllerTests` 覆盖 controller -> canonical providerExtensions。
- 更新 `OpenAiNativeGatewayChatRuntimeTests` 覆盖 canonical -> Spring AI request。
- 执行定向 Gradle test 与横切组合回归。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.infra.config.web.GlobalApiExceptionHandlerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiModelsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests"`：通过。

## 遗留问题

- Stored Chat Completion 的 list/get/update/delete/messages 未实现，仍归属 `TASK-20260514-017` 后续切片。
- deprecated `functions/function_call` 的语义转换已由 `TASK-20260515-005` 闭环，`response_format` 的强类型映射已由 `TASK-20260515-006` 闭环，`modalities/audio/web_search_options` 的强类型映射已由 `TASK-20260515-007` 闭环。
- Chat list/update/delete 的 pagination 与 metadata filter 仍需与 `TASK-20260514-030` 的 pagination 横切任务联动。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
- 官方 Chat Completions API Reference：https://platform.openai.com/docs/api-reference/chat/create-chat-completion
