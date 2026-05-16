# TASK-20260515-005 OpenAI Chat legacy functions/function_call 语义兼容

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260514-017](../backlog/TASK-20260514-017-openai-chat-completions-full-parity.md)  
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[TASK-20260515-003](../done/TASK-20260515-003-openai-chat-create-parameter-parity-foundation.md)

## 背景

OpenAI Chat Completions 仍存在历史客户端使用 deprecated `functions` 与 `function_call` 的情况。当前请求 DTO 已接收这些字段，但 mapper 只把它们放进 `providerExtensions`，没有转换成 canonical tools/tool_choice；runtime 还可能在 OpenAI Direct 请求里同时下发 `tools` 与 legacy `functions`，容易造成语义重复或不一致。

## 目标

- 将 legacy `functions` 转换为 canonical `tools`，让执行层与现代 `tools` 语义统一。
- 将 legacy `function_call` 转换为 canonical `tool_choice`：`auto/none` 原样保留，`{"name":"x"}` 转为 `{"type":"function","function":{"name":"x"}}`。
- 当显式 `tools/tool_choice` 已存在时，优先使用显式现代字段，并只补充不重复的 legacy function definitions。
- OpenAI Direct runtime 不再额外透传 legacy `functions/function_call` 到 `extraBody`；OpenAI-compatible Generic 保留 raw legacy 字段用于兼容旧式后端。
- 增加 controller mapper 与 native runtime 回归测试，证明 legacy 字段不再被静默丢失。

## 非目标

- 不移除 DTO 对 deprecated 字段的接收能力。
- 不实现 provider-specific function call 结果执行；本轮只处理请求语义映射。
- 不改变非 OpenAI Chat 入口。

## 输入

- `OpenAiChatCompletionRequest`
- `OpenAiChatCompletionRequestMapper`
- `OpenAiNativeGatewayChatRuntime`
- `OpenAiToolMapper`
- `OpenAiChatCompletionsControllerTests`
- `OpenAiNativeGatewayChatRuntimeTests`
- `ProviderExecutionSupportServiceTests`

## 输出

- legacy functions/function_call 到 canonical tools/tool_choice 的转换逻辑。
- OpenAI Direct 与 OpenAI-compatible 的 `extraBody` 分流。
- 定向测试与任务回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionRequestMapper.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntime.java`
- `src/main/java/com/prodigalgal/xaigateway/provider/adapter/openai/OpenAiToolMapper.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntimeTests.java`
- `src/test/java/com/prodigalgal/xaigateway/provider/adapter/ProviderExecutionSupportServiceTests.java`
- `tasks/index.md`

## 依赖

- `TASK-20260515-003` 已让 DTO 接收 `functions` 与 `function_call`。
- 现有 canonical `CanonicalToolDefinition` 与 runtime tool mapper。

## 风险

- 同时携带现代 `tools` 和 legacy `functions` 时可能出现重复 function name，需要去重。
- legacy `function_call` object 缺少 `name` 时如果静默忽略，会造成客户请求行为不确定；本轮需要明确报错。
- OpenAI-compatible 站点可能仍依赖 raw legacy 字段，所以不能一刀切删除。

## 验收标准

- legacy `functions` 请求进入 canonical request 后能看到对应 tools。
- legacy `function_call` object 进入 canonical request 后能看到 modern tool_choice object。
- OpenAI Direct native request 的 `extraBody` 不包含 raw `functions/function_call`。
- OpenAI-compatible native request 仍可在 `extraBody` 保留 raw legacy 字段。
- 定向测试通过。

## 测试边界

- 更新 `OpenAiChatCompletionsControllerTests` 验证 mapper/controller 语义。
- 更新 `OpenAiNativeGatewayChatRuntimeTests` 验证 runtime 下发分流。
- 更新 `ProviderExecutionSupportServiceTests` 验证 Spring AI `FunctionTool` 的 name/description 不反向。

## 实现结果

- `OpenAiChatCompletionRequestMapper` 将 deprecated `functions` 转换为 canonical `CanonicalToolDefinition`，并按 function name 去重；显式现代 `tools` 优先，legacy `functions` 只补充不重复定义。
- `function_call` 在没有显式 `tool_choice` 时转换为 canonical `tool_choice`：文本 `auto/none` 原样保留，object `{name}` 转为 modern function tool choice object。
- legacy `functions/function_call` 的原始字段仍保留在 `providerExtensions`，便于审计和 OpenAI-compatible fallback。
- `OpenAiNativeGatewayChatRuntime` 对 OpenAI Direct 不再把 raw legacy `functions/function_call` 放入 `extraBody`，避免和 modern `tools/tool_choice` 双轨重复；OpenAI-compatible Generic 仍保留 raw legacy 字段。
- 修正 `OpenAiNativeGatewayChatRuntime` 与 `OpenAiToolMapper` 调用 Spring AI `Function(description, name, parameters, strict)` 时 name/description 参数顺序反向的问题。

## 验证结果

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportServiceTests"`
- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.infra.config.web.GlobalApiExceptionHandlerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiModelsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceStoredChatTests" --tests "com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportServiceTests"`

## 遗留问题

- 本轮只处理 request-side legacy function semantics；tool call result 执行、函数调用编排与跨 provider 工具结果差异仍归属后续工具生态任务。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
