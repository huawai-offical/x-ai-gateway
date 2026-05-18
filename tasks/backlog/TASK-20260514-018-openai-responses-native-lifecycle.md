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
- [TASK-20260515-019 OpenAI Responses Stream Obfuscation 字段基线](../done/TASK-20260515-019-openai-responses-stream-obfuscation-baseline.md)：Responses stream delta events 已支持 `stream_options.include_obfuscation`，默认输出非空 `obfuscation`，显式 `false` 时关闭，并覆盖 text/reasoning/function call arguments delta。
- [TASK-20260515-020 OpenAI Responses input_tokens 与 compact 本地基线](../done/TASK-20260515-020-openai-responses-input-tokens-compact-baseline.md)：`POST /v1/responses/input_tokens` 与 `POST /v1/responses/compact` 已具备本地 deterministic estimate / emulation 入口、公开文档和 OpenAPI snapshot。
- [TASK-20260515-021 OpenAI Responses include Query 参数基线](../done/TASK-20260515-021-openai-responses-include-query-baseline.md)：stored Response retrieve/input_items 已接收 `include` query 参数，并在公开 OpenAPI 中声明本地 no-op acceptance 边界。
- [TASK-20260515-022 OpenAI Responses Native JSON 原始对象透传基线](../done/TASK-20260515-022-openai-responses-native-json-passthrough-baseline.md)：OpenAI Direct 非流式 Responses create 已保留上游原始 JSON，并在 controller 返回时只将 `model` 重写为 public model。
- [TASK-20260515-023 OpenAI Responses Native Stream SSE 透明转发基线](../done/TASK-20260515-023-openai-responses-native-stream-sse-passthrough-baseline.md)：OpenAI Direct `stream=true` Responses create 已走原生 upstream SSE，并透明转发 event/data/sequence/unknown fields；非 native raw 路径继续使用 canonical Responses stream encoder。
- [TASK-20260516-001 OpenAI Responses 远端生命周期 Passthrough 基线](../done/TASK-20260516-001-openai-responses-remote-lifecycle-passthrough-baseline.md)：OpenAI Direct native create + `store=true` 已记录 upstream lineage，并对带 lineage 的 stored Response 支持远端 retrieve/delete/cancel/input_items passthrough，返回前保持本地 `resp_...` id。
- [TASK-20260516-002 OpenAI Responses input_tokens Native Passthrough](../done/TASK-20260516-002-openai-responses-input-tokens-native-passthrough.md)：OpenAI Direct `responses/input_tokens` 已优先走 native passthrough，route 不可用或非 OpenAI Direct 时回退本地 deterministic estimate，上游已执行后的错误状态不被吞掉。
- [TASK-20260516-003 OpenAI Responses compact Native Passthrough](../done/TASK-20260516-003-openai-responses-compact-native-passthrough.md)：OpenAI Direct `responses/compact` 已优先走 native passthrough，route 不可用或非 OpenAI Direct 时回退本地 opaque marker emulation，上游已执行后的错误状态不被吞掉。
- [TASK-20260517-002 OpenAI Responses 无 Lineage 远端 Lifecycle Route Hint](../done/TASK-20260517-002-openai-responses-untracked-remote-lifecycle-route-hints.md)：未知远端 `resp_...` id 在本地无 lineage 时，只有显式提供 `model` query 或 `X-AI-Gateway-OpenAI-Model` header 才会对 retrieve/delete/cancel/input_items 走 OpenAI Direct route-hint passthrough；无 hint 继续保持本地 not found。

## 剩余切片

- 任意未知远端 `resp_...` id 的无 hint 盲路由保持非目标；真实 OpenAI Direct Responses smoke 由 `TASK-20260514-031` 的受控 key/预算/record-replay 体系继续承接。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
