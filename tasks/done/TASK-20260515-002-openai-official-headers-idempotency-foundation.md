# TASK-20260515-002 OpenAI 官方 Headers 与 Idempotency-Key 下发基线

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-030](../done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[TASK-20260515-001](TASK-20260515-001-openai-error-request-id-foundation.md)

## 背景

OpenAI 兼容不只包含 body 参数，官方客户端还会依赖 `OpenAI-Organization`、`OpenAI-Project`、`OpenAI-Beta`、`Idempotency-Key` 等 headers。此前 `Responses` 入口已有部分 Codex/client metadata 捕获，`Chat Completions` 入口缺少同类 metadata；`OpenAiNativeGatewayChatRuntime` 的上游 header map 只处理 Grok 会话头，尚未把 OpenAI Direct 专属 headers 安全下发。

## 目标

- 在 OpenAI `Chat Completions` 与 `Responses` ingress 捕获官方组织、项目与幂等 headers。
- 把官方 headers 写入 `CanonicalRequestMetadata`，供路由、观测和上游下发复用。
- Native OpenAI runtime 仅在 `OPENAI_DIRECT` upstream 下发 `OpenAI-Organization`、`OpenAI-Project`、`Idempotency-Key`，避免泄露给 OpenAI-compatible、Grok、Azure 等非 OpenAI Direct 站点。
- 增加单元/Controller 测试覆盖 header 捕获、元数据传递和上游 header 白名单策略。

## 非目标

- 不在本轮实现幂等响应持久化或重放缓存。
- 不扩展所有 resource passthrough controller 的 header 入参。
- 不改变已有 Codex session affinity、prompt cache key 或 Grok `x-grok-conv-id` 行为。

## 输入

- `OpenAiChatCompletionsController`
- `OpenAiResponsesController`
- `OpenAiChatCompletionRequestMapper`
- `CanonicalRequestMetadata`
- `OpenAiNativeGatewayChatRuntime`
- 既有 OpenAI ingress/runtime tests

## 输出

- `CanonicalRequestMetadata` 增加 `openAiOrganization`、`openAiProject`、`idempotencyKey`，并保留旧构造入口兼容既有调用。
- `OpenAiChatCompletionsController` 捕获官方 OpenAI headers 并传入 canonical metadata。
- `OpenAiResponsesController` 在保留 Codex metadata 的同时捕获官方 OpenAI headers。
- `OpenAiNativeGatewayChatRuntime.upstreamHeaders` 仅对 `OPENAI_DIRECT` 下发官方组织、项目和幂等 headers；Grok 继续只下发 `x-grok-conv-id`。
- Controller/runtime tests 覆盖 header 捕获、metadata 传递和白名单下发。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsController.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionRequestMapper.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/canonical/CanonicalRequestMetadata.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntime.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntimeTests.java`
- `tasks/index.md`

## 依赖

- `TASK-20260514-030` 横切协议总任务。
- `TASK-20260515-001` 已建立 OpenAI path 与 request id/error envelope 基线。

## 风险

- 组织和项目 headers 属于 OpenAI Direct 语义，误下发给兼容厂商可能泄露租户信息。
- `Idempotency-Key` 本轮只做 header 保真，不代表 gateway 已具备本地幂等缓存。
- Chat mapper 增加 metadata 后不能破坏既有 `providerExtensions` 参数保真。

## 验收标准

- `/v1/chat/completions` 能把 `OpenAI-Organization`、`OpenAI-Project`、`Idempotency-Key` 捕获到 `CanonicalRequestMetadata`。
- `/v1/responses` 保留 Codex metadata 的同时捕获官方 headers。
- `OpenAiNativeGatewayChatRuntime.upstreamHeaders(OPENAI_DIRECT, request)` 输出官方 headers。
- 非 `OPENAI_DIRECT` 站点不输出官方组织/project/idempotency headers。
- 定向测试通过。

## 测试边界

- 更新 `OpenAiChatCompletionsControllerTests` 与 `OpenAiResponsesControllerTests`。
- 更新 `OpenAiNativeGatewayChatRuntimeTests`。
- 执行定向 Gradle test 与横切组合回归。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.infra.config.web.GlobalApiExceptionHandlerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiModelsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests"`：通过。

## 遗留问题

- `Idempotency-Key` 已完成 header 保真与 OpenAI Direct 下发，但本地幂等响应持久化、重复请求重放和冲突检测仍需单独任务承接。
- Files/Uploads/Batches 等 resource passthrough controller 的官方 header 捕获不在本轮范围内，后续需要在资源族任务中补齐。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

