# REQ-20260517-005 OpenAI Vector Store File Batches 本地 Lifecycle 基线

状态：Done
日期：2026-05-17
上游来源：[TASK-20260514-023](../../tasks/backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 背景

`TASK-20260517-003` 已补 Vector Stores 本地 lifecycle，`TASK-20260517-004` 已补 Vector Store Files 本地 attachment lifecycle。官方 Vector Store File Batches 仍缺入口，导致 SDK 或 OpenAI-compatible 客户端无法用批量方式把多个 `file_id` 挂到同一个 Vector Store，也缺少批量过程的可审计状态。

## 目标

- 按官方 Vector Store File Batches 资源面补齐本地基线：
  - `POST /v1/vector_stores/{vectorStoreId}/file_batches`
  - `GET /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}`
  - `POST /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/cancel`
  - `GET /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files`
- 使用 `gateway_async_resource` 记录 `vector_store.file_batch` 本地对象。
- 批量 create 在同一事务中校验 `file_ids` 非空、无重复、且当前 vector store 中不存在 active attachment。
- 批量 create 成功后，为每个 `file_id` 创建 `VECTOR_STORE_FILE` attachment，并同步 parent vector store `file_counts`。
- `cancel` 对已完成本地批次返回明确错误，避免把已经完成的本地 attachment 假装取消。
- 同步 public docs、OpenAPI snapshot、provider catalog 和父任务状态。

## 范围

- `GatewayAsyncResourceService`
- `GatewayAsyncResourceType`
- `GatewayPublicResourceService`
- `OpenAiVectorStoresController`
- Public API docs、OpenAPI、provider catalog
- Controller/service/docs/provider catalog 测试

## 非目标

- 不实现真实向量入库或异步 ingestion worker。
- 不实现 Vector Store search。
- 不实现 file content。
- 不让 Responses `file_search` tool 真执行。
- 不访问真实 OpenAI 或创建上游托管 Vector Store。
- 不新增官方文档中没有的独立 `GET /file_batches` 列表端点。

## 方案

- 新增 `GatewayAsyncResourceType.VECTOR_STORE_FILE_BATCH`，resource key 使用 `vsfb_`。
- File Batch response payload 返回 `object=vector_store.file_batch`、`status=completed`、`file_counts`。
- File Batch metadata 保存 `vector_store_id`、`file_ids` 和事件流；公开 response 不暴露私有 metadata。
- create 前先完成全量输入校验和重复 attachment 校验，再写入 batch 与 file attachment，保证失败时不会留下半批次记录。
- `list batch files` 读取 batch metadata 中的 `file_ids`，再按当前 active attachment 返回 OpenAI list envelope，并支持 `after`、`limit`、`order`、`filter`。
- `cancel` 如果批次已经是 `completed`，返回 `invalid_request_error`；后续若接入真实异步 ingestion，再扩展 `in_progress -> cancelled` 状态迁移。

## 风险

- 批量过程如果边校验边写入，可能在部分失败后留下不一致 attachment。
- 同一 `file_id` 跨不同 vector store 可以复用，但同一 vector store 内必须拒绝重复。
- 本地同步完成的 batch 不能被误解为上游真实向量索引完成。
- `file_counts` 必须与 active attachment 保持一致。

## 验收标准

- create/retrieve/cancel/list files 均有 controller 与 service 测试。
- create 校验空数组、重复 `file_ids`、已存在 attachment，并且失败路径不保存 batch。
- create 成功后 parent vector store `file_counts.total/completed` 增加批次数量。
- `cancel` 已完成批次返回清晰错误，不修改已有 attachment。
- `list batch files` 支持分页、排序与 status filter。
- docs/provider catalog/OpenAPI 明确 File Batches 本地 lifecycle 已支持，search/file content/真实 ingestion 仍是后续切片。

## 测试边界

- `GatewayAsyncResourceVectorStoreFileBatchesTests`
- `OpenAiVectorStoreFileBatchesControllerTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`

## 实现结果

- 新增 `GatewayAsyncResourceType.VECTOR_STORE_FILE_BATCH`。
- `GatewayAsyncResourceService` 增加 file batch create/retrieve/cancel/list files。
- `OpenAiVectorStoresController` 公开官方 File Batches 路径：
  - `POST /v1/vector_stores/{vectorStoreId}/file_batches`
  - `GET /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}`
  - `POST /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/cancel`
  - `GET /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files`
- 批量 create 支持 `file_ids` 与 `files` 两种输入；会先完成非空、重复 file id、已存在 attachment 校验，再创建 batch 与 attachment。
- Public docs、OpenAPI、provider catalog、审计报告和父任务已同步 `openai.vector-store-file-batches-local-lifecycle`。

## 验证情况

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoresTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreFilesTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreFileBatchesTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoresControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoreFilesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoreFileBatchesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

结果：通过。

## 遗留问题

- File content、vector store search、真实 vector ingestion、Responses `file_search` tool resource binding 和真实 smoke 仍属于 `TASK-20260514-023` 后续切片。
- 本轮没有访问真实 OpenAI Vector Store File Batches API，不把本地 batch completion 解释为上游托管 ingestion 完成。

## 当前状态

- 2026-05-17：已完成并归档。
