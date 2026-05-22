# TASK-20260517-004 OpenAI Vector Store Files 本地 Attachment Lifecycle 基线

状态：Done
优先级：High
类型：子任务
父任务：[TASK-20260514-023](../done/TASK-20260514-023-openai-vector-stores-full-stack.md)
上游来源：[REQ-20260517-004](../../docs/requirements/REQ-20260517-004-openai-vector-store-files-local-attachment-lifecycle.md)

## 背景

`TASK-20260517-003` 已补 Vector Store 本地 lifecycle，但 Vector Store Files 子资源仍无入口。下一步需要先做本地 attachment lifecycle，继续缩小 `TASK-20260514-023` 的高优缺口。

## 目标

- 新增 OpenAI-compatible Vector Store File attach/list/retrieve/delete。
- 使用 `gateway_async_resource` 保存当前 Distributed Key 下的本地 attachment。
- list 支持 `after`、`limit`、`order`、`filter`。
- create/delete 更新 parent vector store 的 `file_counts`。
- 同步 public docs、OpenAPI、provider catalog 和父任务。

## 非目标

- 不实现 file content。
- 不实现 file batches。
- 不实现 vector store search。
- 不让 Responses `file_search` tool 真执行。
- 不访问真实 OpenAI。

## 输入

- `GatewayAsyncResourceService`
- `GatewayAsyncResourceRepository`
- `OpenAiVectorStoresController`
- `TASK-20260514-023`
- OpenAI Vector Store Files API Reference

## 输出

- Vector store file service methods
- Controller/service/docs/provider catalog tests
- 文档与任务回写

## 影响范围

- OpenAI ingress。
- Async resource storage。
- Public compatibility docs 和 OpenAPI。
- Provider catalog conformance。

## 依赖

- [TASK-20260517-003](TASK-20260517-003-openai-vector-stores-local-lifecycle-baseline.md)
- `TASK-20260514-021` Files lifecycle 后续会影响 content 子资源。

## 风险

- 本地 attachment 和真实 ingestion 混淆。
- 同 file id 跨 vector store 复用导致重复判定错误。
- parent `file_counts` 与 attachment 状态不一致。

## 验收标准

- create/list/get/delete 全部可通过本地测试验证。
- 重复 attach 同一 vector store 的同一 file id 被拒绝。
- list cursor 与 limit/order/filter 行为稳定。
- docs/provider catalog 明确 search/file_batches/file_search 仍是剩余切片。

## 测试边界

- `OpenAiVectorStoreFilesControllerTests`
- `GatewayAsyncResourceVectorStoreFilesTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`

## 关联文档

- [REQ-20260517-004](../../docs/requirements/REQ-20260517-004-openai-vector-store-files-local-attachment-lifecycle.md)
- [TASK-20260514-023](../done/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 实现结果

- 新增 `GatewayAsyncResourceType.VECTOR_STORE_FILE`。
- `GatewayAsyncResourceService` 增加 `createVectorStoreFile`、`listVectorStoreFiles`、`getVectorStoreFile`、`deleteVectorStoreFile`。
- `OpenAiVectorStoresController` 公开 `/v1/vector_stores/{vectorStoreId}/files` attach/list/retrieve/delete。
- Attachment payload 返回 `object=vector_store.file`，`id` 保持客户端传入的 `file_id`，存储记录使用内部 `vsf_` resource key，避免同一 file id 跨 vector store 复用时互相冲突。
- 同一 Distributed Key、同一 vector store、同一 `file_id` 只允许一个 active attachment。
- create/delete 同步 parent vector store 的 `file_counts`。
- Public docs、OpenAPI snapshot、provider catalog、审计报告和父任务已同步 `openai.vector-store-files-local-attachment`。

## 验证情况

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoresTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreFilesTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoresControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoreFilesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

结果：通过。

## 遗留问题

- File content、file_batches、vector store search、真实 vector ingestion 和 Responses `file_search` tool resource binding 仍属于 `TASK-20260514-023` 后续切片。
- 本轮没有访问真实 OpenAI Vector Store Files API，不把本地 attachment 解释为上游托管 ingestion 完成。

## 当前状态

- 2026-05-17：已完成并归档。
