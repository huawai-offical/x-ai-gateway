# TASK-20260515-014 OpenAI Streaming Event Usage 与 Sequence 基线

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260514-030](../backlog/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)  
上游来源：[TASK-20260514-017](../backlog/TASK-20260514-017-openai-chat-completions-full-parity.md)、[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)

## 背景

OpenAI Chat Completions stream 使用 `chat.completion.chunk`，`stream_options.include_usage=true` 时会在 `[DONE]` 前额外返回 `choices=[]` 且带 `usage` 的 chunk。Responses stream 使用语义化 SSE event，事件对象包含 `type` 与 `sequence_number`，用于客户端按序处理事件。当前项目已有 Chat/Responses streaming 基线，但 Chat 尚未输出 include_usage 终块，Responses 事件也没有 sequence number，不能作为横切协议兼容闭环证据。

## 目标

- Chat stream 在 `stream_options.include_usage=true` 时，于 `data: [DONE]` 前输出 usage chunk。
- Chat stream chunk 使用同一个 `id` 与 `created`，避免同一流内每个 chunk 看起来像不同 completion。
- Responses stream 每个 SSE payload 增加单调递增 `sequence_number`。
- 保持现有 text delta、tool call delta、reasoning delta、completed event 的事件名不变。
- 补充 controller/encoder 测试和公开文档说明。

## 非目标

- 不在本任务内实现完整 Realtime WebSocket 事件代理。
- 不新增 OpenAI Responses native HTTP executor。
- 不处理 stream resume、断线重连或上游原始 SSE 透明转发。
- 不实现 Responses `stream_options.include_obfuscation` 的真实混淆字段。

## 输入

- OpenAI Chat Completions streaming API Reference。
- OpenAI Responses streaming guide 与 streaming events API Reference。
- 当前 `OpenAiChatCompletionEncoder`、`OpenAiResponsesEncoder` 和 controller tests。

## 输出

- Chat stream include_usage usage chunk 基线。
- Responses stream sequence_number 基线。
- 单元测试覆盖 usage chunk、稳定 chunk id、Responses sequence_number。
- public docs bundle 与 Markdown 说明更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsController.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionEncoder.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionResponse.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesEncoder.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java`
- `docs/public-api-compatibility.md`
- `tasks/index.md`

## 依赖

- `CanonicalStreamEvent` 的 completed event 需要带 usage，才能生成 usage chunk。
- Responses stream 仍依赖当前 canonical stream，不改变上游执行器。

## 风险

- 如果上游流没有 completed event，本地无法合成最终 usage chunk；本轮保持无 completed event 时不伪造 usage。
- Chat chunk 顶层增加 `usage` 字段，客户端应按 OpenAI SDK 行为接受 null 或对象。
- Responses sequence_number 是本地编码顺序，不代表 OpenAI 上游原始序号。

## 验收标准

- Chat `stream_options.include_usage=true` 会在 `[DONE]` 前输出一个 `choices=[]` 且 `usage.total_tokens` 有值的 chunk。
- Chat 同一次 stream 内 role/content/finish/usage chunk 的 `id` 和 `created` 一致。
- Responses stream payload 包含从 0 开始单调递增的 `sequence_number`。
- 文档明确 Chat include_usage 与 Responses sequence_number 的兼容边界。

## 测试边界

- Controller WebFlux tests 覆盖 Chat 与 Responses streaming 输出。
- Public docs bundle tests 覆盖公开说明。
- 不跑真实 OpenAI stream smoke。

## 关联文档

- [TASK-20260514-030](../backlog/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 实现结果

- Chat stream 现在会把 `stream_options` 传入 `OpenAiChatCompletionEncoder`。
- Chat stream 每次响应生成稳定的 `chatcmpl-{requestId}` 与统一 `created`，role/content/tool/finish/usage chunk 保持一致。
- Chat `stream_options.include_usage=true` 时，在 `data: [DONE]` 前输出 `choices=[]` 且带 `usage` 的 chunk。
- Responses stream 每个 SSE payload 增加从 0 开始递增的 `sequence_number`，覆盖 created/in_progress/output/content/text/tool/reasoning/completed 等本地编码事件。
- public docs bundle、public OpenAPI snapshot 与 `docs/public-api-compatibility.md` 已说明 Chat include_usage 与 Responses sequence_number 边界。

## 验证结果

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"
```

覆盖项：

- Chat stream 基线 chunk 与 `[DONE]`。
- Chat stream 同一流内 chunk `id` / `created` 稳定。
- Chat include_usage usage chunk 的 `choices=[]` 与 `usage.total_tokens`。
- Responses stream payload 的 `sequence_number` 单调递增。
- public docs bundle conformance check：`openai.streaming-event-usage-sequence`。

## 遗留问题

- 完整 Realtime WebSocket 事件代理、session event 双向转发、断线恢复不在本任务内，继续归属 Realtime/Responses 原生生命周期任务。
- Responses `sequence_number` 是 gateway 本地编码顺序，不代表 OpenAI 上游原始 SSE 序号。
- Responses `stream_options.include_obfuscation` 尚未实现真实 obfuscation 字段，需要在原生 Responses executor 或透明上游 SSE 任务中继续处理。
