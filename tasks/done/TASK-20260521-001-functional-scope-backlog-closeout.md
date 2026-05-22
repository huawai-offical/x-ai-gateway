# TASK-20260521-001 功能性服务 API Backlog 收口与优先级清理

状态：Completed
优先级：Critical
类型：子任务
父任务：[TASK-20260514-016](../backlog/TASK-20260514-016-functional-service-api-coverage-parent.md)
上游来源：[REP-20260521 功能性服务 API Backlog 收口审计](../../docs/reports/REP-20260521-functional-scope-backlog-closeout.md)

## 背景

`TASK-20260514-029` 已完成 public OpenAPI、SDK 示例和 coverage matrix 派生收尾。继续推进项目时，当前最大风险已经不是某个新增 endpoint，而是 Backlog 中仍存在旧“全量 API 覆盖”口径任务，可能把后续计划重新拉向 Fine-tuning、Batches、Evals、Admin、Containers 或 provider-specific batch/job/admin。

## 目标

- 关闭已由 done 子任务覆盖、仅剩真实 smoke 或 out-of-scope 扩展的历史 Backlog 任务。
- 保留并收紧真正还需要后续开发的功能性支撑任务。
- 更新任务索引，让 Codex/UI/UX 优先级和下一步队列可直接执行。

## 非目标

- 不新增后端 endpoint。
- 不运行自动化测试、浏览器回归或真实 smoke。
- 不删除历史归档任务文件。

## 输入

- `tasks/index.md`
- `tasks/done/TASK-20260514-013*.md`
- `tasks/backlog/TASK-20260514-016*.md`
- `tasks/done/TASK-20260514-017*.md`
- `tasks/done/TASK-20260514-018*.md`
- `tasks/done/TASK-20260514-019*.md`
- `tasks/backlog/TASK-20260514-020*.md`
- `tasks/backlog/TASK-20260514-021*.md`
- `tasks/done/TASK-20260514-023*.md`
- `tasks/done/TASK-20260514-030*.md`
- `tasks/backlog/TASK-20260514-031*.md`

## 输出

- 已移动到 `tasks/done/` 的历史闭环任务。
- 已收紧的 `TASK-20260514-020`、`TASK-20260514-021`。
- 更新后的 `tasks/index.md`。
- 本任务归档至 `tasks/done/`。

## 影响范围

- 本地任务文档和报告，不涉及业务代码。

## 依赖

- [TASK-20260514-029](../done/TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md) 已完成。
- [ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md) 已确立产品范围。

## 风险

- 误把真实未完成能力标成 Done。处理时必须区分“当前产品范围已闭环”与“官方全量 parity 未实现”。
- `Batches` 一词在 Vector Store File Batches 中仍是对话/RAG 支撑能力，不能误删或误判。

## 验收标准

- 已完成主线任务从 `tasks/backlog/` 移至 `tasks/done/`，并说明剩余不再作为当前产品范围任务。
- `TASK-20260514-020` 与 `TASK-20260514-021` 不再以全量 parity、Videos、Batches、fine-tuning 为目标。
- `tasks/index.md` 中剩余 Backlog 能解释为功能性支撑或暂缓 smoke。
- 目标文件 `git diff --check` 通过。

## 测试边界

本任务为任务治理和文档整理，不运行自动化测试。验证方式为目标文件搜索和 `git diff --check`。

## 当前状态

- 2026-05-21：进入 In Progress，准备执行 Backlog 收口。
- 2026-05-21：已完成 Backlog 收口，`TASK-20260514-013/017/018/019/023/030` 移动至 `tasks/done/`，`TASK-20260514-020/021` 重写为功能性支撑任务，`TASK-20260514-031` 继续作为暂缓 smoke。

## 实现结果

- 已将 Chat/Responses、Chat lifecycle、Responses native、Conversations/Webhooks/tools、Vector Stores、Cross-cutting 协议相关历史父级任务从 Backlog 归档到 Done。
- 已把 `TASK-20260514-020` 收紧为“OpenAI 多模态支撑参数边界收紧”。
- 已把 `TASK-20260514-021` 收紧为“OpenAI Files、Uploads、Models 功能性支撑面”。
- 已更新 `tasks/index.md` 顶部 P0 队列和功能性 API 任务表。
- 已清理已移动任务的旧 `backlog/` 链接。

## 验证结果

- `tasks/backlog/` 当前只剩 `TASK-20260514-016`、`TASK-20260514-020`、`TASK-20260514-021`、`TASK-20260514-031`。
- 针对已移动任务的旧 `backlog/TASK-20260514-013/017/018/019/023/030` 链接搜索无命中。
- 目标文件 `git diff --check` 通过后归档。
