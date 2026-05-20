# REP-20260518 对话与 Tools 功能性 API Backlog 重规划

状态：Done
日期：2026-05-18
上游来源：[REQ-20260518-005](../requirements/REQ-20260518-005-functional-service-api-scope.md)、[ADR-0010](../decisions/ADR-0010-functional-service-api-scope.md)

## 背景

用户已明确要求 Fine-tuning / Batches / Evals / Admin 等非核心官方 API out；Anthropic、Gemini、Vertex、Codex 和其它主流 AI provider 也不追求官方全量 API，而是统一按 OpenAI API 标准功能区收紧。当前任务体系需要从“OpenAI 全量覆盖”重规划为“对话、streaming、tools/function calling、RAG/file_search 支撑、横切协议和真实 smoke”。

## 重规划结论

- 当前 backlog 只保留功能性服务 API 相关任务。
- `TASK-20260514-014` 和 `TASK-20260514-015` 已由本轮范围清理实际闭环，归档到 `tasks/done/`。
- OpenAI `/v1/batches`、`/v1/fine_tuning/jobs*`、Anthropic `/v1/messages/batches*`、Gemini batch prediction、public `/api/v1/tunings` 不再作为公开支持面。
- `REQ-20260518-006` / `TASK-20260518-006` 已进一步删除内部 Batch/Tuning/Anthropic Message Batch 兼容代码，不再保留历史数据兼容层。
- Anthropic、Gemini、Vertex、Codex 的 native/provider-specific API 也不再按官方全集扩展；只保留可映射到 OpenAI 标准功能区的 Messages/GenerateContent/Responses、tools、embeddings/files 等支撑能力。
- Vector Store File Batches 保留为 Responses `file_search` 的本地 ingestion 支撑，不等同于 OpenAI `/v1/batches`。

## 剩余 Backlog 优先级

| 优先级 | 任务 | 定位 |
| --- | --- | --- |
| P0 Critical | `TASK-20260514-016` | 对话与 Tools 功能性 API 总控父任务，维护范围和验收基线 |
| P0 Critical | `TASK-20260514-017` | Chat Completions 参数、tools、streaming、stored lifecycle 主路径 |
| P0 Critical | `TASK-20260514-018` | Responses 原生 passthrough、生命周期和输入项主路径 |
| P0 Critical | `TASK-20260514-030` | 错误、headers、idempotency、rate limit、path matcher 等横切协议 |
| P0 Critical | `TASK-20260514-031` | 真实 smoke、成本防护、record/replay 与可用 key 验证 |
| P1 High | `TASK-20260514-013` | Chat/Responses 参数保真总览，承接 017/018/019 的边界一致性 |
| P1 High | `TASK-20260514-019` | Conversations、Webhooks、Responses tools 和 function tools 生态 |
| P1 High | `TASK-20260514-020` | Audio、Images、Embeddings、Moderations 功能性参数 parity |
| P1 High | `TASK-20260514-021` | Files、Uploads、Models 的对话/RAG 支撑最小生命周期 |
| P1 High | `TASK-20260514-023` | Vector Stores 对话 RAG/file_search 支撑面 |
| P0 Critical | `TASK-20260514-029` | 2026-05-19 起优先执行 Codex OpenAPI、Catalog、Conformance、SDK 示例事实源收紧 |

## 已移出当前范围

- `TASK-20260514-022` OpenAI Fine-tuning 全生命周期
- `TASK-20260514-024` OpenAI Containers 与 Code Interpreter 文件
- `TASK-20260514-025` OpenAI Videos API 兼容面
- `TASK-20260514-026` OpenAI Evals、Graders 与 Runs API
- `TASK-20260514-027` OpenAI Skills API 与工具分发
- `TASK-20260514-028` OpenAI Administration API 权限隔离与只读优先

## 后续执行口径

- 优先把 P0 的 Chat、Responses、横切协议和真实 smoke 闭环。
- P1 只做对话、tools 或 RAG/file_search 直接支撑能力，不扩展到官方 provider 全量 lifecycle。
- P2 负责保持 docs/OpenAPI/catalog/conformance/SDK 与代码事实一致。
- 真实 smoke 使用当前可用 key 时，只覆盖对话、tools、files/models/vector stores/realtime client secret 等功能性服务面；不再 smoke Fine-tuning、Batches、Evals 或 provider admin。
- Anthropic、Gemini、Vertex、Codex 的后续 smoke 和 conformance 也按同一标准区执行：拒绝 message batches、batch prediction、tuning、evals、pipeline/job/admin 和非 Responses Codex 内部接口。
