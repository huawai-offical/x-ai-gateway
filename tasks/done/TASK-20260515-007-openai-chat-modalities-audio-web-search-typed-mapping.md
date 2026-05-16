# TASK-20260515-007 OpenAI Chat modalities/audio/web_search_options 强类型映射

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260514-017](../backlog/TASK-20260514-017-openai-chat-completions-full-parity.md)  
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[TASK-20260515-003](../done/TASK-20260515-003-openai-chat-create-parameter-parity-foundation.md)、[TASK-20260515-006](../done/TASK-20260515-006-openai-chat-response-format-typed-mapping.md)

## 背景

Chat create 已接收 `modalities`、`audio` 与 `web_search_options`，但当前 native runtime 仍通过 `extraBody` flatten 方式保真下发。Spring AI OpenAI API 已提供 `OutputModality`、`AudioParameters` 与 `WebSearchOptions` 强类型字段，继续 raw flatten 会降低参数校验与官方字段契约的一致性。

## 目标

- 将 `modalities` 映射为 Spring AI `OutputModality`，支持 `text` 与 `audio`。
- 将 `audio.voice` 与 `audio.format` 映射为 Spring AI `AudioParameters`。
- 将 `web_search_options.search_context_size` 与 `user_location.approximate` 映射为 Spring AI `WebSearchOptions`。
- 对非法 enum、非法 JSON shape 做 OpenAI-style `invalid_argument` 前置错误。
- 清理 `extraBody` 中对 `modalities/audio/web_search_options` 的重复 flatten。

## 非目标

- 不实现音频输出结果的二进制解码或流式音频事件。
- 不实现 Responses API 的 web search tool 原生执行器。
- 不改变 provider-specific web search adapter 的现有行为。

## 输入

- `OpenAiChatCompletionRequest`
- `OpenAiChatCompletionRequestMapper`
- `OpenAiNativeGatewayChatRuntime`
- `OpenAiChatCompletionsControllerTests`
- `OpenAiNativeGatewayChatRuntimeTests`

## 输出

- Chat modalities/audio/web_search_options typed mapping。
- 非法 enum 与非法 shape 的 controller 回归。
- Runtime 强类型字段断言与 `extraBody` 去重断言。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntime.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionRequestMapper.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntimeTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java`
- `tasks/index.md`

## 依赖

- `TASK-20260515-003` 已让 DTO 接收相关字段。
- `TASK-20260515-006` 已建立复杂 Chat 参数强类型化模式。

## 风险

- `modalities` 包含 `audio` 时缺少 `audio` 对象会导致上游失败；本轮在本地提前拒绝。
- Spring AI 当前只暴露部分 web search 字段，未来 OpenAI 若扩展字段需要后续刷新。
- OpenAI-compatible 站点可能依赖 raw flatten；但 typed 字段序列化仍是官方字段名，本轮不再重复下发同名字段。

## 验收标准

- `modalities:["text","audio"]` 映射为 `OutputModality.TEXT/AUDIO`。
- `audio.voice/format` 映射为对应 enum，且缺失或非法时报错。
- `web_search_options.search_context_size` 和 approximate location 可在 native request 上读取。
- `extraBody` 不再包含 `modalities/audio/web_search_options`。
- 定向与组合测试通过。

## 测试边界

- 更新 `OpenAiNativeGatewayChatRuntimeTests` 覆盖 typed mapping。
- 更新 `OpenAiChatCompletionsControllerTests` 覆盖非法 enum error envelope。

## 实现结果

- `OpenAiNativeGatewayChatRuntime` 将 `modalities` 映射为 Spring AI `OutputModality`，当前支持 `text` 与 `audio`。
- `audio.voice` 与 `audio.format` 映射为 Spring AI `AudioParameters` enum，并在缺失或非法时返回 OpenAI-style `invalid_argument`。
- `web_search_options.search_context_size`、`user_location.type` 和 `user_location.approximate` 映射为 Spring AI `WebSearchOptions`。
- `modalities` 包含 `audio` 时，如果缺少 `audio` 参数，会在本地提前拒绝。
- `extraBody` 不再重复 flatten `modalities/audio/web_search_options`。

## 验证结果

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests"`
- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.infra.config.web.GlobalApiExceptionHandlerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiModelsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceStoredChatTests" --tests "com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportServiceTests"`

## 遗留问题

- Spring AI 当前只暴露 web search 的 `search_context_size` 与 approximate location；如果 OpenAI 后续扩展 `web_search_options`，需要后续跟进 API/changelog refresh。
- 本轮只处理 request-side typed mapping，不处理音频输出结果解析或 Responses tool web search 原生执行器。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
