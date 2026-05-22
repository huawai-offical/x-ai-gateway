# REP-20260521 功能性服务 API Backlog 收口审计

状态：Completed
日期：2026-05-21
关联需求：[REQ-20260518-005](../requirements/REQ-20260518-005-functional-service-api-scope.md)、[REQ-20260519-002](../requirements/REQ-20260519-002-codex-priority-functional-service-api.md)

## 背景

`TASK-20260514-029` 完成后，当前最高优先 Codex 事实源已经闭环。但 `tasks/backlog/` 仍保留若干历史任务，部分任务标题或剩余切片仍带有 `full parity`、`full stack`、`Batches`、`Containers` 等旧口径，容易让后续执行再次偏离“对话、streaming、tools、多模态与必要支撑能力”的产品范围。

## 审计结论

- `TASK-20260514-013`、`017`、`018`、`019`、`023`、`030` 的功能性服务 API 主体已由多个 done 子任务闭环；剩余项主要是真实 smoke、测试证据或已经 out-of-scope 的 hosted/containers/realtime 扩展，不应继续作为 Backlog 开发任务。
- `TASK-20260514-020` 已按 coverage matrix 重写边界并归档：只保留 Embeddings、Audio transcriptions/speech、Images generations、Moderations 的功能性支撑面；Audio translations、Images edits/variations、Videos parity 不进入当前必做队列。
- `TASK-20260514-021` 已按 coverage matrix 重写边界并归档：只保留 Files、Uploads、Models 对话/RAG 支撑面；OpenAI `/v1/batches` 和 fine-tuning 相关入口只作为历史归档，不再进入 backlog。
- `TASK-20260514-031` 保留为真实 smoke 与认证成本防护任务；按用户当前指令暂不执行测试和真实网络。

## 收口结果

- 已归档：`TASK-20260514-013`、`017`、`018`、`019`、`023`、`030`。
- 已完成并归档：`TASK-20260514-020` 多模态支撑参数边界、`TASK-20260514-021` Files/Uploads/Models 功能性支撑面。
- 继续保留 Backlog：`TASK-20260514-031` 真实 smoke。
- `TASK-20260514-016` 继续作为总控父任务保留在 Backlog，用于承接剩余两个非测试开发任务和一个暂缓 smoke 任务。

## 本轮处理范围

- 将已闭环、仅剩 smoke 或 out-of-scope 的历史父/子任务从 `tasks/backlog/` 移动到 `tasks/done/`，并回写“剩余不再作为当前产品范围任务”的原因。
- 将 `TASK-20260514-020`、`TASK-20260514-021` 标题和内容收紧为功能性服务 API 支撑面。
- 更新 `tasks/index.md` 顶部 P0 队列和“对话与 Tools 功能性 API 任务体系”表。
- 不修改业务代码，不运行测试，不执行真实 smoke。

## 验收标准

- Backlog 中不再出现已由 done 子任务闭环的 Chat/Responses/Conversations/Webhooks/Vector Stores/Cross-cutting 父级任务。
- Backlog 中不再以“全量 API / full parity / full stack / Batches”作为未来开发目标。
- 剩余 Backlog 只保留当前可解释的功能性支撑任务和暂缓的真实 smoke 任务。
- 所有移动任务都有当前状态、验证边界和后续处理说明。
