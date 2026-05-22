# TASK-20260515-006 OpenAI Chat response_format 强类型映射

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-017](../done/TASK-20260514-017-openai-chat-completions-full-parity.md)
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[TASK-20260515-003](../done/TASK-20260515-003-openai-chat-create-parameter-parity-foundation.md)

## 背景

Chat create 已接收 `response_format`，但当前 native runtime 只通过 `extraBody` flatten 保真下发。Spring AI OpenAI API 已提供 `ResponseFormat` 强类型字段，如果继续走 raw extra body，会削弱结构化输出的类型检查和官方字段位置一致性。

## 目标

- 将 Chat `response_format.type=text/json_object/json_schema` 转换为 Spring AI `ResponseFormat`。
- 对 `json_schema` 的 `name/schema/strict` 做明确映射，避免结构化输出 schema 被静默丢失。
- OpenAI Direct 请求不再通过 `extraBody` 重复下发 `response_format`。
- 保留 request mapper 中 raw `response_format` 到 `providerExtensions`，便于审计和后续 conformance。
- 增加 runtime 与 controller 测试，覆盖 `json_schema`、`json_object` 和非法 type。

## 非目标

- 不处理 Responses API 的 `text.format` 强类型映射。
- 不实现 `modalities/audio/web_search_options` 的强类型转换。
- 不引入真实 OpenAI smoke；本轮只做本地 contract。

## 输入

- `OpenAiChatCompletionRequest`
- `OpenAiChatCompletionRequestMapper`
- `OpenAiNativeGatewayChatRuntime`
- `OpenAiChatCompletionsControllerTests`
- `OpenAiNativeGatewayChatRuntimeTests`

## 输出

- Chat response_format typed mapping。
- 非法 response_format type 的 OpenAI-style error regression。
- 定向测试与任务回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntime.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntimeTests.java`
- `tasks/index.md`

## 依赖

- `TASK-20260515-003` 已让 DTO 接收 `response_format`。
- `TASK-20260515-001` 已提供 OpenAI-style error envelope。

## 风险

- `json_schema` schema 必须是 JSON object；如果宽松接收错误类型，会让上游错误更晚暴露。
- Spring AI `ResponseFormat.JsonSchema` builder 默认 strict 为 true，需要保留请求中的显式 strict。
- OpenAI-compatible 站点可能依赖 raw flatten，但 typed `response_format` 序列化仍是同一官方字段。

## 验收标准

- `json_schema` 请求映射为 `ResponseFormat.Type.JSON_SCHEMA`，且保留 name/schema/strict。
- `json_object` 请求映射为 `ResponseFormat.Type.JSON_OBJECT`。
- OpenAI Direct `extraBody` 不包含 `response_format`。
- 非法 `response_format.type` 返回 OpenAI-style `invalid_argument`。
- 定向测试通过。

## 测试边界

- 更新 `OpenAiNativeGatewayChatRuntimeTests` 覆盖 typed mapping。
- 更新 `OpenAiChatCompletionsControllerTests` 覆盖非法 type error envelope。

## 实现结果

- `OpenAiNativeGatewayChatRuntime` 将 `response_format.type=text/json_object/json_schema` 映射到 Spring AI `ResponseFormat` 强类型字段。
- `json_schema` 映射保留 `name`、`schema` 与显式 `strict`，并在 schema 缺失或类型错误时尽早报错。
- Chat request mapper 增加 `response_format` 基线校验，非法 `type` 或非法 `json_schema` 会返回 OpenAI-style `invalid_argument`。
- OpenAI Direct 和 OpenAI-compatible 均通过 typed `response_format` 字段下发，`extraBody` 不再重复 flatten `response_format`，避免重复字段。

## 验证结果

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests"`
- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.infra.config.web.GlobalApiExceptionHandlerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiModelsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceStoredChatTests" --tests "com.prodigalgal.xaigateway.provider.adapter.ProviderExecutionSupportServiceTests"`

## 遗留问题

- 本轮只处理 Chat `response_format`；`modalities/audio/web_search_options` 的强类型转换已由 `TASK-20260515-007` 闭环。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
