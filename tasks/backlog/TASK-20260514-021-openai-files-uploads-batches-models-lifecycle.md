# TASK-20260514-021 OpenAI Files、Uploads、Models 对话支撑最小生命周期

状态：Backlog  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 背景

当前项目已有 Files、Uploads、Batches 和 Models 的部分路径。根据 [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)，OpenAI `/v1/batches` 和 fine-tuning 相关能力不再作为产品范围；本任务收窄为对话、tools、RAG/file_search 所需的 Files、Uploads 和 Models 最小支撑面。

## 目标

- 补齐 Files list query、pagination、purpose filter、delete/content 语义。
- 补齐 Uploads create/parts/complete/cancel 的官方参数和状态机。
- 移除 OpenAI `/v1/batches` 公开支持面和后续任务目标。
- 补齐 Models list/get；`DELETE /v1/models` 仅保留历史本地 registry 边界，不再继续扩展 fine-tuned model upstream owner-role delete passthrough。

## 非目标

- Fine-tuning lifecycle 已由 `REQ-20260518-005` 判定为范围外。
- Batches lifecycle 已由 `REQ-20260518-005` 判定为范围外。
- Vector Stores 文件批次由 `TASK-20260514-023` 覆盖。

## 输入

- 官方 Files、Uploads、Models API Reference。
- `OpenAiFilesController`、`OpenAiUploadsController`、`OpenAiBatchesController`、`OpenAiModelsController`。

## 输出

- 对话/RAG 支撑所需的最小对象生命周期 controller/service。
- Pagination 和 lineage 测试。
- public OpenAPI 与 conformance 更新。

## 影响范围

- File storage、async resource service、resource execution service、request lifecycle、conformance fixtures。

## 依赖

- `TASK-20260514-030` pagination/error/idempotency 基线。

## 风险

- 本地对象与上游对象混合会出现权限和 lineage 混淆。
- Delete 操作必须保护用户隔离。

## 验收标准

- OpenAI `/v1/batches` 不再作为公开支持面。
- Models delete 不再继续扩展 fine-tuned upstream owner-role passthrough。
- Files/Uploads 对象状态迁移可重复验证。

## 测试边界

- Service 状态机测试。
- Controller tests。
- 真实 smoke：file upload/list/get/delete 与 models list/get；不再覆盖 batches。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 已完成切片

- [TASK-20260516-011 OpenAI Batches List Envelope 与本地游标分页](../done/TASK-20260516-011-openai-batches-list-envelope.md)：`GET /v1/batches` 已返回当前 DistributedKey 下经 gateway 创建并落库的 Batch lineage list envelope；该端点已从 accepted exception 中移除。
- [TASK-20260516-012 OpenAI Models Delete 与 Fine-tuned Model 删除边界](../done/TASK-20260516-012-openai-models-delete-finetuned-boundary.md)：`DELETE /v1/models/{model}` 已删除当前 DistributedKey 下经 gateway fine-tuning/import 登记的 fine-tuned model registry，并保护公共模型与跨租户模型。

2026-05-18 范围收窄后，`TASK-20260516-011` 作为历史归档保留，OpenAI `/v1/batches` 不再属于当前公开支持面。
