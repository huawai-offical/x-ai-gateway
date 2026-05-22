# TASK-20260515-001 OpenAI 错误 Envelope 与 Request Id 基线

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-030](../done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、用户要求持续推进项目逐个闭环任务

## 背景

OpenAI API 全量兼容不只要求 endpoint 和参数一致，还要求错误对象、request id、trace 入口和 headers 能被客户端稳定识别。此前 `GlobalApiExceptionHandler` 统一返回本项目 `ApiErrorResponse(code,message,traceId)`，`TraceIdWebFilter` 只写回 `X-Trace-Id`，OpenAI 协议入口缺少 OpenAI-style `{"error":{...}}` envelope 与 `X-Request-Id` 响应头基线。

## 目标

- OpenAI 协议入口发生错误时返回 OpenAI-style error envelope。
- 响应头同时写回 `X-Trace-Id` 与 `X-Request-Id`，并复用传入 `X-Request-Id`。
- 非 OpenAI 协议入口继续保留当前 `ApiErrorResponse`，避免破坏 Anthropic/Gemini/admin/portal 现有前端。
- 增加 WebFlux 测试覆盖 OpenAI path 与非 OpenAI path 的差异。

## 非目标

- 不在本轮实现 idempotency 持久化。
- 不在本轮实现 rate limit headers、pagination、webhook signature。
- 不改变业务 controller 的成功响应形态。

## 输入

- `GlobalApiExceptionHandler`
- `TraceIdWebFilter`
- OpenAI ingress path 清单
- 既有协议 controller tests

## 输出

- 新增 `OpenAiApiErrorResponse`，固定 OpenAI-style `error.message`、`error.type`、`error.param`、`error.code` 结构。
- `TraceIdWebFilter` 响应同时写回 `X-Trace-Id` 与 `X-Request-Id`，并优先复用传入 header。
- `GlobalApiExceptionHandler` 对 OpenAI 协议路径输出 OpenAI error envelope，对 `/v1/messages` 等非 OpenAI 路径保留 gateway 自有 envelope。
- 新增 `GlobalApiExceptionHandlerTests` 并同步 OpenAI ingress 旧断言。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/infra/config/web/GlobalApiExceptionHandler.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/config/web/OpenAiApiErrorResponse.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/config/web/TraceIdWebFilter.java`
- `src/test/java/com/prodigalgal/xaigateway/infra/config/web/GlobalApiExceptionHandlerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiModelsControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `tasks/index.md`

## 依赖

- `TASK-20260514-030` 横切协议总任务。
- 现有 `TraceIdWebFilter` trace id 生成逻辑。

## 风险

- `/v1/messages` 是 Anthropic compatible 入口，不能误判为 OpenAI error envelope。
- 现有前端依赖 `ApiErrorResponse`，OpenAI envelope 只能作用于 OpenAI 协议路径。

## 验收标准

- `/v1/chat/completions` 抛出参数错误时返回 `error.message`、`error.type`、`error.code`。
- 非 OpenAI path 抛出参数错误时仍返回顶层 `code`。
- 响应头包含 `X-Request-Id`，且传入该 header 时原样回显。
- 定向测试通过。

## 测试边界

- 新增 `GlobalApiExceptionHandlerTests` 覆盖 OpenAI path 与 Anthropic-compatible path。
- 执行定向 Gradle test。
- 同步执行 OpenAI/Anthropic/Gemini 代表性 ingress 回归，确认横切错误体变更未污染非 OpenAI 协议。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.infra.config.web.GlobalApiExceptionHandlerTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiModelsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GeminiGenerateContentControllerTests" --tests "com.prodigalgal.xaigateway.infra.config.web.GlobalApiExceptionHandlerTests"`：通过。

## 遗留问题

- Idempotency、rate limit headers、pagination、webhook signature、stream/realtime event 横切契约仍归属父任务 `TASK-20260514-030` 后续切片。
- OpenAI path 清单后续需要随 endpoint 补齐继续维护，避免新增 endpoint 仍输出 gateway 自有错误体。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

