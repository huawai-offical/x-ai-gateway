# REQ-20260514-008 OpenAI API 完整兼容性深度审计

## 背景

用户询问当前项目的 OpenAI API 是否已经完全实现、是否完全兼容参数，并要求深度排查。项目近期已闭环 OpenAI/xAI Responses parity，但这不等于覆盖 OpenAI API 全部资源族和全部参数。本轮需要以官方 OpenAI API 文档为事实源，对照当前仓库实现，给出明确结论、证据和缺口任务。

## 目标

- 对 OpenAI API 主要资源族和参数面做完整度审计。
- 区分“OpenAI-compatible Chat 兼容”“Responses 参数保真”“OpenAI 官方 API 全量兼容”三类不同结论。
- 对缺失、弱兼容、文档声明不准或测试覆盖不足的项目生成本地任务。
- 输出可追溯报告，包含官方来源、本地代码证据、风险和优先级。

## 非目标

- 不在本轮直接实现所有缺口。
- 不使用非官方文档作为事实源。
- 不把 xAI、Anthropic、Gemini 的兼容结论混入 OpenAI 官方 API 全量兼容结论。

## 审计范围

- Chat Completions、Responses、Realtime/Streaming。
- Embeddings、Images、Audio、Files、Uploads、Vector Stores、Batches、Fine-tuning、Moderations、Models。
- Assistants/Threads/Runs、Evals、Containers、Administration 等官方 API 面是否存在项目入口或降级声明。
- SDK 示例、公开 OpenAPI、provider catalog、conformance tests 与 runtime mapper 是否一致。

## 验收标准

- 给出“是否完全实现/完全兼容参数”的明确结论。
- 每个主要 API 面至少有官方来源和本地实现证据。
- 缺口必须转为 task spec，并关联本需求与审计报告。
- 报告需要说明已支持、部分支持、缺失、需官方 key smoke 验证的边界。

## 完成结果

- 已完成 [REP-20260514 OpenAI API 完整兼容性深度审计](../reports/REP-20260514-openai-api-compatibility-deep-audit.md)。
- 结论：当前项目不是 OpenAI API 全量实现，也不是 OpenAI API 全量参数兼容；当前是 OpenAI-compatible 核心 Chat/Responses 与部分 OpenAI 官方资源生命周期的基础兼容。
- 已创建缺口任务：
  - [TASK-20260514-013 OpenAI Chat/Responses 参数全量保真与原生 Responses 边界](../../tasks/backlog/TASK-20260514-013-openai-chat-responses-native-parity.md)
  - [TASK-20260514-014 OpenAI 官方资源族覆盖差距补齐](../../tasks/backlog/TASK-20260514-014-openai-resource-family-coverage-gap.md)
  - [TASK-20260514-015 OpenAI 公开 OpenAPI、catalog 与 conformance 事实源校准](../../tasks/backlog/TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md)

## 状态

Done。
