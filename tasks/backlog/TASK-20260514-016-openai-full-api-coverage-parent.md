# TASK-20260514-016 对话与 Tools 功能性 API 覆盖总控父任务

状态：Backlog  
优先级：Critical  
类型：父任务  
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)、[ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md)

## 背景

本父任务最初承接 OpenAI API 全量覆盖目标。2026-05-18 用户已明确收窄产品范围：OpenAI、Anthropic、Gemini、Vertex、Codex 等主流 provider 不再追求全量官方 API，而是统一按 OpenAI 标准功能区做对话、streaming、tools/function calling 以及必要的 RAG/file_search 支撑。Fine-tuning、Batches、Evals、Administration 等非核心官方 API 已不再作为 backlog 目标。

2026-05-19 用户要求先不做测试、全速推进项目任务，并将 Codex 相关任务提升为最高优先。Codex 后续执行顺序以 [TASK-20260519-002](../done/TASK-20260519-002-codex-priority-functional-service-api.md) 为当前 P0 队列；该父任务已完成队列重排并归档。

## 目标

- 维护对话与 tools 功能性 API 的任务树和验收基线。
- 统一 Chat/Responses/Conversations/Webhooks/tools/file_search/Models/Files 最小支撑面的 coverage matrix、conformance matrix 与 public docs。
- 统一 Anthropic Messages、Gemini/Vertex GenerateContent、Codex Responses smoke 与 OpenAI 标准功能区之间的边界说明。
- 确保任务完成后都能回写支持状态，并明确非核心官方 API 不再推进。
- 给后续真实 smoke 和 provider 兼容验证提供优先级顺序。

## 非目标

- 不在父任务里直接实现具体 API。
- 不追求 OpenAI Direct 或其它 provider 的官方 API 全量 parity。
- 不实现 Fine-tuning、Batches、Evals/Graders/Runs、官方 Administration API、官方 Videos parity、Skills 分发 API、官方 Containers 全量 lifecycle。
- 不实现 Anthropic message batches、Gemini/Vertex batch prediction、Vertex pipeline/job/admin 或非 Responses 的 Codex 内部 API。

## 输入

- 官方 OpenAI API Reference。
- [REP-20260514 OpenAI API 完整兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)。
- [REQ-20260518-005 对话与 Tools 功能性服务 API 范围收窄](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)。
- 既有 `TASK-20260514-013`、`014`、`015`。
- [REP-20260518 对话与 Tools 功能性 API Backlog 重规划](../../docs/reports/REP-20260518-functional-service-api-backlog-replan.md)。

## 输出

- 保留和收窄后的对话/tools 子任务。
- 删除或范围外记录后的非核心官方 API 任务。
- 对话/tools coverage matrix、验收证据、真实 smoke 分类。
- 每批完成后的 docs/tasks 回写。

## 影响范围

- OpenAI/Anthropic/Gemini/Vertex/Codex ingress、runtime executors、resource executors、provider catalog、public OpenAPI、conformance fixtures、admin/portal 展示。

## 依赖

- 官方文档刷新机制。
- 真实 OpenAI/Gemini/Anthropic key 和成本预算，优先用于低成本对话/tools smoke。

## 风险

- 旧任务、docs 或 provider catalog 仍可能残留“全量 API”口径。
- `batch` 一词也用于 Vector Store File Batches，清理时不能误删 file_search ingestion 支撑能力。
- 内部 `/admin/*` 是平台管理后台，不等同于官方 provider Administration API，不能误删。

## 验收标准

- 当前 backlog 只保留对话、tools 和直接支撑能力任务。
- 非核心官方 API 不再作为未来必做项出现在 backlog、provider catalog 支持面或 public docs 支持面。
- 子任务完成后 coverage matrix 和 public docs 同步更新。

## 测试边界

- 父任务以任务治理和 coverage matrix 校验为主。
- 具体 API 测试由子任务负责。

## 关联任务

- [TASK-20260514-013](TASK-20260514-013-openai-chat-responses-native-parity.md)
- [TASK-20260514-014](../done/TASK-20260514-014-openai-resource-family-coverage-gap.md)
- [TASK-20260514-015](../done/TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md)
- [TASK-20260518-005](../done/TASK-20260518-005-functional-service-api-scope-pruning.md)
- [TASK-20260518-006](../done/TASK-20260518-006-non-core-api-code-eradication.md)
- [TASK-20260519-002](../done/TASK-20260519-002-codex-priority-functional-service-api.md)
