# TASK-20260514-020 OpenAI Audio、Images、Embeddings、Moderations 参数 parity

状态：Backlog  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 背景

当前 Embeddings、Moderations、Images generation 多为 JSON passthrough；Audio multipart、Images edits/variations 只接收有限字段。要全量覆盖，需要逐 endpoint 对齐 multipart/JSON 参数和响应类型。

## 目标

- 补齐 Audio transcriptions/translations/speech 参数、streaming 和 response format。
- 补齐 Images generations/edits/variations 参数，包括 multipart 多文件、mask、background、output format、partial images 等。
- 校准 Embeddings dimensions/encoding_format/user 等参数。
- 校准 Moderations 分类、模型与响应结构。

## 非目标

- Videos 独立由 `TASK-20260514-025` 覆盖。
- 不保证第三方 provider 支持所有 OpenAI 多模态参数。

## 输入

- 官方 Audio、Images、Embeddings、Moderations API Reference。
- `OpenAiAudioController`、`OpenAiImagesController`、Embeddings/Moderations resource executors。

## 输出

- 参数 parity matrix。
- multipart 参数透传实现。
- 多模态 conformance tests 与真实 smoke。

## 影响范围

- OpenAI resource controllers、multipart executor、file refs、public OpenAPI、SDK examples。

## 依赖

- `TASK-20260514-031` 成本与真实 smoke 防护。

## 风险

- 多模态真实 smoke 成本高，需要预算上限。
- multipart 字段类型容易和 WebFlux binding 冲突。

## 验收标准

- 每个 endpoint 的官方参数都有处理状态。
- multipart 参数不再因 controller 签名限制被静默丢失。
- 至少覆盖 Audio、Images、Embeddings、Moderations 各一条真实或 mock conformance。

## 测试边界

- Controller multipart tests。
- Resource executor tests。
- 真实 smoke 按 key 和预算分类执行。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

