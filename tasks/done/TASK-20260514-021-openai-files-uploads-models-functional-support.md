# TASK-20260514-021 OpenAI Files、Uploads、Models 功能性支撑面

状态：Completed  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-016](../backlog/TASK-20260514-016-functional-service-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](../done/TASK-20260514-014-openai-resource-family-coverage-gap.md)、[REP-20260521](../../docs/reports/REP-20260521-functional-scope-backlog-closeout.md)

## 背景

当前项目已有 Files、Uploads 和 Models 的功能性支撑路径。根据 [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)，OpenAI `/v1/batches` 和 fine-tuning 相关能力不再作为产品范围；本任务只保留对话、tools、RAG/file_search 所需的 Files、Uploads 和 Models 最小支撑面。

## 目标

- 补齐 Files list query、pagination、purpose filter、delete/content 语义。
- 补齐 Uploads create/parts/complete/cancel 的官方参数和状态机。
- 保持 OpenAI `/v1/batches` 不在公开支持面和后续任务目标中。
- 补齐 Models list/get 的功能性模型发现语义；`DELETE /v1/models` 仅作为历史本地 registry 边界，不再继续扩展 fine-tuned model upstream owner-role delete passthrough。

## 非目标

- Fine-tuning lifecycle 已由 `REQ-20260518-005` 判定为范围外。
- OpenAI `/v1/batches` lifecycle 已由 `REQ-20260518-005` 判定为范围外。
- Vector Stores 文件批次由 `TASK-20260514-023` 覆盖。

## 输入

- `src/main/resources/functional-service-api-coverage-matrix.json`
- `OpenAiFilesController`、`OpenAiUploadsController`、`OpenAiModelsController`。
- `docs/openapi/public-openapi.json`

## 输出

- 对话/RAG 支撑所需的最小对象生命周期 controller/service 边界。
- Pagination、purpose filter、lineage 和状态机补齐计划或实现。
- public OpenAPI 与 conformance 更新。

## 影响范围

- File storage、async resource service、resource execution service、request lifecycle、conformance fixtures。

## 依赖

- `TASK-20260514-030` pagination/error/idempotency 基线。

## 风险

- 本地对象与上游对象混合会出现权限和 lineage 混淆。
- Delete 操作必须保护用户隔离。

## 验收标准

- OpenAI `/v1/batches` 不再作为公开支持面或未来开发目标。
- Models delete 不再继续扩展 fine-tuned upstream owner-role passthrough。
- Files/Uploads 对象状态迁移可重复验证。

## 测试边界

- Service 状态机测试。
- Controller tests。
- 真实 smoke：file upload/list/get/delete 与 models list/get；不再覆盖 OpenAI `/v1/batches`。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)
- [REP-20260521 功能性服务 API Backlog 收口审计](../../docs/reports/REP-20260521-functional-scope-backlog-closeout.md)
- [Files、Uploads、Models 功能性支撑面](../../docs/files-uploads-models-functional-support.md)

## 已完成切片

- [TASK-20260516-011 OpenAI Batches List Envelope 与本地游标分页](../done/TASK-20260516-011-openai-batches-list-envelope.md)：`GET /v1/batches` 已返回当前 DistributedKey 下经 gateway 创建并落库的 Batch lineage list envelope；该端点已从 accepted exception 中移除。
- [TASK-20260516-012 OpenAI Models Delete 与 Fine-tuned Model 删除边界](../done/TASK-20260516-012-openai-models-delete-finetuned-boundary.md)：`DELETE /v1/models/{model}` 已删除当前 DistributedKey 下经 gateway fine-tuning/import 登记的 fine-tuned model registry，并保护公共模型与跨租户模型。

2026-05-18 范围收窄后，`TASK-20260516-011` 作为历史归档保留，OpenAI `/v1/batches` 不再属于当前公开支持面。

## 实现结果

- `GET /v1/files` 已接收 `purpose`、`limit`、`after`、`order`，并返回 `has_more`、`first_id`、`last_id` 的 OpenAI-compatible list envelope。
- `GatewayFileService` 新增租户内 purpose filter、cursor after、limit 1-100 与 asc/desc 排序。
- `FileObjectGatewayResourceExecutor` 的 `/v1/files` GET 路径返回 list envelope，不再只返回裸数组。
- public OpenAPI 文件列表参数描述已更新，`docs/files-uploads-models-functional-support.md` 固化 Files/Uploads/Models 支撑边界。
- `OpenAiModelsController` 当前仅保留 `GET /v1/models` 与 `GET /v1/models/{model}`；未恢复 `DELETE /v1/models`。
- public OpenAPI 与 conformance matrix 中未恢复 `/v1/batches` 或 `/v1/fine_tuning/jobs*`。

## 验证记录

- `.\gradlew.bat compileJava -x test`
- `.\gradlew.bat compileTestJava -x test`
- `docs/openapi/public-openapi.json` 与 `src/test/resources/conformance/endpoint-conformance-matrix.json` JSON 解析通过。
- public OpenAPI 中 `/v1/files` GET 参数包含 `purpose,limit,after,order`。
- `src/main/java`、`docs/openapi/public-openapi.json` 与 conformance matrix 中未发现 `/v1/batches`、`/v1/fine_tuning` 或 `DELETE /v1/models` 公开支持残留；`functional-service-api-coverage-matrix.json` 仅保留 out-of-scope 记录。
- 未执行单元测试与真实 smoke，符合用户当前“先不做测试”的要求。

## 当前状态

- 2026-05-21：从“Files/Uploads/Batches/Models lifecycle”收紧为“Files/Uploads/Models 功能性支撑面”，保留在 Backlog，等待后续非测试实现切片。
- 2026-05-21：进入 In Progress，优先补 Files list 的 `purpose/limit/after/order` 参数与 OpenAI-compatible list envelope。
- 2026-05-21：已完成 Files list 参数、list envelope、public docs 与范围外残留验证，移动到 Done。
