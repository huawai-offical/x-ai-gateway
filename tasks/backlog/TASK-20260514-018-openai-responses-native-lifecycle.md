# TASK-20260514-018 OpenAI Responses 原生执行器与生命周期

状态：Backlog  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-013](TASK-20260514-013-openai-chat-responses-native-parity.md)

## 背景

当前 `/v1/responses` 入口存在，但执行路径仍主要复用 Chat runtime，不等价于 OpenAI Responses API 的原生对象、事件和 lifecycle 语义。

## 目标

- 新增 OpenAI Direct Responses 原生 HTTP executor。
- 覆盖 create/get/delete/cancel/input_items/count/compact。
- 保留官方 Responses streaming event schema、usage、reasoning、output item 和 error shape。
- 对 OpenAI-compatible Generic 明确 emulated 与 native 的差异。

## 非目标

- 不实现 Conversations 和 Webhooks，交由 `TASK-20260514-019`。
- 不实现 Vector Stores 和 Containers，交由 `023`、`024`。

## 输入

- 官方 Responses API Reference。
- `OpenAiResponsesController`、`OpenAiResponsesRequestMapper`、`OpenAiResponsesEncoder`。

## 输出

- Responses native executor。
- Responses lifecycle controllers。
- Responses conformance fixtures 和 smoke harness。

## 影响范围

- Responses controller、runtime execution、response encoding、streaming、resource storage、request lifecycle log。

## 依赖

- `TASK-20260514-030` 横切协议兼容。
- 真实 OpenAI key，用于原生 Responses smoke。

## 风险

- 响应对象 shape 改动可能影响当前 portal/workbench。
- Streaming event 与现有 canonical stream 不完全一致，需要双轨编码。

## 验收标准

- OpenAI Direct `/v1/responses` 不再通过 ChatCompletionRequest 执行。
- `include`、`previous_response_id`、`store`、`background`、`stream_options` 等字段不被静默丢失。
- lifecycle endpoints 和 streaming event 有测试证据。

## 测试边界

- Controller tests、executor tests、stream encoder tests。
- 真实 smoke：create + stream + get + delete。

## 已完成切片

- [TASK-20260515-014 OpenAI Streaming Event Usage 与 Sequence 基线](../done/TASK-20260515-014-openai-streaming-event-usage-sequence-baseline.md)：当前 canonical Responses stream 编码已补齐语义化 SSE event 的本地 `sequence_number`，并覆盖 text/reasoning/function call/completed 基线测试。
- [TASK-20260515-017 OpenAI Responses Native HTTP Create 基线](../done/TASK-20260515-017-openai-responses-native-http-create-baseline.md)：OpenAI Direct 非流式 Responses create 已改走原生 `/v1/responses` HTTP JSON POST，保留 Responses 原始字段、官方 headers 和 usage 映射，不再通过 Spring AI `ChatCompletionRequest` 执行。
- [TASK-20260515-018 OpenAI Responses 本地生命周期 cancel/input_items 基线](../done/TASK-20260515-018-openai-responses-local-lifecycle-cancel-input-items.md)：本地 stored Response 已支持 retrieve/delete/cancel 与 input_items list envelope，其中 cancel 限定 `background=true` 且未终态，input_items 支持 `after`、`limit`、`order`。

## 剩余切片

- 上游原始 SSE 透明转发、`stream_options.include_obfuscation` 和完整 lifecycle endpoints 仍未闭环。
- `responses/input_tokens`、compact/count、远端 cancel passthrough、原始 Responses 对象完整透传到 public API 的双轨输出仍需独立设计，当前 controller create 仍保持 canonical encoder 兼容形态。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
