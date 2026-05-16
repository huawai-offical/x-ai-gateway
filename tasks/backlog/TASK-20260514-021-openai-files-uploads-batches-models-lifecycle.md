# TASK-20260514-021 OpenAI Files、Uploads、Batches、Models 对象生命周期

状态：Backlog  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 背景

当前项目已有 Files、Uploads、Batches 和 Models 的部分路径，但 Files list 是本地 catalog，Batches 缺 list，Models 缺 delete，Uploads 需要对齐官方路径与响应语义。

## 目标

- 补齐 Files list query、pagination、purpose filter、delete/content 语义。
- 补齐 Uploads create/parts/complete/cancel 的官方参数和状态机。
- 补齐 Batches list/create/get/cancel、error file lineage 和 endpoint allowlist。
- 补齐 Models list/get/delete，区分 public model 与 fine-tuned model。

## 非目标

- Fine-tuning lifecycle 由 `TASK-20260514-022` 覆盖。
- Vector Stores 文件批次由 `TASK-20260514-023` 覆盖。

## 输入

- 官方 Files、Uploads、Batches、Models API Reference。
- `OpenAiFilesController`、`OpenAiUploadsController`、`OpenAiBatchesController`、`OpenAiModelsController`。

## 输出

- 完整对象生命周期 controller/service。
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

- Batches list 不再作为 accepted exception。
- Models delete 有权限边界和 negative tests。
- Files/Uploads/Batches 对象状态迁移可重复验证。

## 测试边界

- Service 状态机测试。
- Controller tests。
- 真实 smoke：file upload + batch create/list/get/cancel。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

