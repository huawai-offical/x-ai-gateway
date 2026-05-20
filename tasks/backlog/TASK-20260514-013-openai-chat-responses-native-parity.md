# TASK-20260514-013 OpenAI Chat/Responses 参数全量保真与原生 Responses 边界

状态：Backlog
优先级：High
类型：子任务
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)
上游来源：[REP-20260514 OpenAI API 完整兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)、[REP-20260518 对话与 Tools 功能性 API Backlog 重规划](../../docs/reports/REP-20260518-functional-service-api-backlog-replan.md)

## 背景

当前 `/v1/chat/completions` 使用强 DTO，参数面只覆盖核心字段；`/v1/responses` 虽保留原始 body，但 OpenAI Direct 执行仍主要经由 Chat Completions runtime，非 function tools 与 Responses 对象语义不完整。2026-05-18 后，本任务只覆盖对话、streaming、tools/function calling 与 file_search/RAG 直接相关的 Chat/Responses 行为，不扩展到 Fine-tuning、Batches、Evals、Administration 等官方非核心 API。

## 目标

- 建立 OpenAI Chat Completions 与 Responses 的参数白名单、保真矩阵和降级策略。
- 让 Chat Completions 至少保真或明确拒绝 `max_completion_tokens`、`metadata`、`modalities`、`n`、`prediction`、`presence_penalty`、`prompt_cache_key`、`prompt_cache_retention`、`response_format`、`safety_identifier`、`seed`、`service_tier`、`stop`、`stream_options`、`store`、`top_logprobs`、`top_p`、`user`、`verbosity`、`web_search_options`。
- 明确 OpenAI Direct Responses 是否走官方 `/v1/responses` passthrough；若继续经 Chat runtime，必须对不支持的字段返回可解释降级，不静默丢失。
- 覆盖 Responses 内置工具、MCP tools、custom tools、`include`、`previous_response_id`、`conversation`、`background`、`max_tool_calls`、`stream_options.include_obfuscation` 的兼容边界。

## 非目标

- 不实现 Vector Stores、Evals、Containers 等资源族。
- 不要求所有第三方 OpenAI-compatible provider 具备 OpenAI Direct 同等能力。
- 不实现 Fine-tuning、OpenAI `/v1/batches`、Evals、Administration、Videos、Skills 或 Containers 全量 lifecycle。

## 输入

- 官方 OpenAI Chat Completions 与 Responses API Reference。
- `OpenAiChatCompletionRequest`、`OpenAiChatCompletionRequestMapper`、`OpenAiResponsesRequestMapper`、`OpenAiNativeGatewayChatRuntime`。
- 现有 `TASK-20260514-009` Responses 字段 parity 结果。

## 输出

- Chat/Responses 参数兼容矩阵。
- 代码实现或明确降级错误。
- 单元测试覆盖关键字段保真、字段拒绝、stream options、Responses tools。
- 文档更新到 public API compatibility 与 provider catalog。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/OpenAiNativeGatewayChatRuntime.java`
- `src/main/resources/provider-catalog.json`
- `docs/public-api-compatibility.md`
- 相关 OpenAI controller/runtime tests。

## 依赖

- OpenAI 官方 API Reference 当前版本。
- Spring AI OpenAI SDK 对 Chat/Responses 字段支持情况；若 SDK 不支持，需要使用受控 passthrough 或自建 HTTP executor。

## 风险

- 盲目透传字段可能影响第三方 OpenAI-compatible provider。
- 原生 Responses passthrough 会改变响应对象和 streaming event shape，需要兼容现有 encoder。

## 验收标准

- Chat Completions 官方高频参数不再被静默丢失。
- Responses 非 function tools 和对象语义有明确支持或明确降级。
- OpenAI Direct 与 OpenAI-compatible Generic 的行为差异在代码、测试、文档中一致。
- 单元测试覆盖至少 10 个新增字段和 3 类 Responses tool。

## 测试边界

- Java mapper/runtime 单测。
- Controller WebFlux test。
- 有真实 OpenAI key 时增加 smoke；无 key 时分类 skipped，不能伪造通过。

## 关联文档

- [REQ-20260514-008](../../docs/requirements/REQ-20260514-008-openai-api-compatibility-deep-audit.md)
- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)
- [REP-20260514 OpenAI API 完整兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)
- [REP-20260518 对话与 Tools 功能性 API Backlog 重规划](../../docs/reports/REP-20260518-functional-service-api-backlog-replan.md)
- [TASK-20260514-009](../done/TASK-20260514-009-openai-xai-responses-field-parity.md)

## 下游细分任务

- [TASK-20260514-017 OpenAI Chat Completions 全参数与对象生命周期](TASK-20260514-017-openai-chat-completions-full-parity.md)
- [TASK-20260514-018 OpenAI Responses 原生执行器与生命周期](TASK-20260514-018-openai-responses-native-lifecycle.md)
- [TASK-20260514-019 OpenAI Conversations、Webhooks 与 Responses 工具生态](TASK-20260514-019-openai-conversations-webhooks-tools.md)
- [TASK-20260514-030 OpenAI 横切协议兼容](TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
