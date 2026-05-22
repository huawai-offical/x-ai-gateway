# TASK-20260517-005 OpenAI Vector Store File Batches 本地 Lifecycle 基线

状态：Done
优先级：High
类型：子任务
父任务：[TASK-20260514-023](../done/TASK-20260514-023-openai-vector-stores-full-stack.md)
上游来源：[REQ-20260517-005](../../docs/requirements/REQ-20260517-005-openai-vector-store-file-batches-local-lifecycle.md)

## 背景

Vector Store File Batches 是批量 attachment 的官方资源面。当前项目已经有 vector store 与单文件 attachment，但还没有 batch object、batch retrieve/cancel/list files，也没有批量校验和失败路径保护。

## 目标

- 新增 OpenAI-compatible Vector Store File Batch create/retrieve/cancel/list files。
- 批量 create 使用同一事务完成输入校验、重复校验、batch 存储、file attachment 创建和 parent counts 更新。
- 失败路径不留下部分 batch 或 attachment。
- 同步 public docs、OpenAPI、provider catalog、审计报告和父任务。

## 非目标

- 不实现真实向量索引、search 或 file content。
- 不实现 Responses `file_search` tool。
- 不新增官方文档中没有的独立 file batch 列表端点。
- 不访问真实 OpenAI。

## 输入

- `GatewayAsyncResourceService`
- `OpenAiVectorStoresController`
- `TASK-20260514-023`
- OpenAI Vector Store File Batches API Reference

## 输出

- `VECTOR_STORE_FILE_BATCH` resource type。
- Service/controller methods 与测试。
- Public docs/OpenAPI/provider catalog 更新。
- 父任务剩余切片更新。

## 影响范围

- OpenAI ingress。
- Async resource storage。
- Public compatibility docs 和 OpenAPI。
- Provider catalog conformance。

## 依赖

- [TASK-20260517-003](TASK-20260517-003-openai-vector-stores-local-lifecycle-baseline.md)
- [TASK-20260517-004](TASK-20260517-004-openai-vector-store-files-local-attachment-lifecycle.md)

## 风险

- 批量 create 写入半批次导致 parent counts 与 attachment 不一致。
- 同一 file id 在同一 vector store 重复 attach。
- 已完成本地 batch 被 cancel 后和已创建 attachment 状态冲突。

## 验收标准

- create/retrieve/cancel/list files controller 与 service 测试通过。
- 重复 `file_ids` 和已存在 attachment 均被拒绝。
- 成功 create 后 parent `file_counts` 与 batch `file_counts` 一致。
- 已完成 batch cancel 返回 OpenAI-style 错误。
- docs/provider catalog/OpenAPI 明确本地 lifecycle 边界。

## 测试边界

- `GatewayAsyncResourceVectorStoreFileBatchesTests`
- `OpenAiVectorStoreFileBatchesControllerTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`

## 关联文档

- [REQ-20260517-005](../../docs/requirements/REQ-20260517-005-openai-vector-store-file-batches-local-lifecycle.md)
- [TASK-20260514-023](../done/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 实现结果

- 新增 `GatewayAsyncResourceType.VECTOR_STORE_FILE_BATCH`，本地 batch id 使用 `vsfb_`。
- `GatewayAsyncResourceService` 增加 `createVectorStoreFileBatch`、`getVectorStoreFileBatch`、`cancelVectorStoreFileBatch`、`listVectorStoreFileBatchFiles`。
- `OpenAiVectorStoresController` 公开官方 File Batches 路径：
  - `POST /v1/vector_stores/{vectorStoreId}/file_batches`
  - `GET /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}`
  - `POST /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/cancel`
  - `GET /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files`
- create 支持 `file_ids` 与 `files` 两种输入，且 `file_ids` 与 `files` 不能同时提供。
- 批量 create 会先完成非空、重复 file id、已存在 attachment 校验，失败路径不会保存 batch。
- 成功 create 后保存 `VECTOR_STORE_FILE_BATCH`，为每个 file 创建 `VECTOR_STORE_FILE` attachment，并同步 parent vector store 的 `file_counts`。
- 本地同步完成的 batch 返回 `status=completed`；对 completed batch 调用 cancel 会返回明确错误，避免和已创建 attachment 状态冲突。
- Batch files list 按 batch metadata 中的 `file_ids` 返回 active attachment list envelope，支持 `after`、`limit`、`order`、`filter`。
- Public docs、OpenAPI snapshot、provider catalog、审计报告和父任务已同步 `openai.vector-store-file-batches-local-lifecycle`。

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
