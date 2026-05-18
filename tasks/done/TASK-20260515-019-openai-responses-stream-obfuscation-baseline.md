# TASK-20260515-019 OpenAI Responses Stream Obfuscation 字段基线

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)  
上游来源：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)、OpenAI Responses API Reference

## 背景

OpenAI Responses streaming 支持 `stream_options.include_obfuscation`。官方说明该选项会在 streaming delta event 上增加 `obfuscation` 字段，用于缓解 payload size 侧信道；当调用方信任网络链路时可设置为 `false` 以节省带宽。当前项目的 Responses stream encoder 已有 `sequence_number`，但没有读取 `stream_options.include_obfuscation`，导致该参数被静默忽略。

## 目标

- `OpenAiResponsesController` 将 request body 中的 `stream_options` 传给 Responses stream encoder。
- Responses stream delta events 在默认或 `include_obfuscation=true` 时包含非空 `obfuscation` 字段。
- 当 `stream_options.include_obfuscation=false` 时，delta events 不输出 `obfuscation` 字段。
- 覆盖 text delta、reasoning delta 与 function call arguments delta 三类 delta event。

## 非目标

- 不在本切片实现上游 raw SSE 透明转发。
- 不保证本地 obfuscation 与 OpenAI 上游随机长度完全一致；本切片只保证字段语义、默认行为和显式关闭行为。
- 不改变 Chat Completions stream。

## 输入

- `OpenAiResponsesController` stream create path。
- `OpenAiResponsesEncoder` 当前 Responses stream event encoder。

## 输出

- Responses stream options 传递。
- Delta event `obfuscation` 字段编码。
- Controller stream 回归测试。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesEncoder.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `docs/public-api-compatibility.md`
- `tasks/backlog/TASK-20260514-018-openai-responses-native-lifecycle.md`

## 依赖

- `TASK-20260515-014` Responses stream `sequence_number` 基线。

## 风险

- 默认开启 obfuscation 会增加 SSE payload；显式 `include_obfuscation=false` 需要作为节省带宽的受控开关。
- 只应给 delta events 添加 obfuscation，不能污染 completed、created 等非 delta event。

## 验收标准

- 未传 `include_obfuscation` 的 Responses stream delta event 包含非空 `obfuscation`。
- 显式 `include_obfuscation=false` 的 Responses stream delta event 不包含 `obfuscation`。
- `sequence_number` 行为保持不变。
- reasoning 与 function call arguments delta 也支持相同逻辑。

## 测试边界

- Controller WebFlux stream tests。
- 不访问真实 OpenAI 远端。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 实现结果

- `OpenAiResponsesController` 已将 `requestBody.stream_options` 传递给 `OpenAiResponsesEncoder`。
- `OpenAiResponsesEncoder` 在默认或 `include_obfuscation=true` 时为 delta events 输出非空 `obfuscation` 字段。
- 显式 `stream_options.include_obfuscation=false` 时不输出 `obfuscation`。
- 覆盖 `response.output_text.delta`、`response.reasoning_summary_text.delta` 与 `response.function_call_arguments.delta`。
- 公开兼容文档、docs bundle 和 OpenAPI snapshot 已同步说明 Responses stream obfuscation 行为。

## 验证情况

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests"`

## 遗留问题

- 本切片不处理 OpenAI Direct 上游原始 SSE 透明转发，仍归属父任务后续切片。
