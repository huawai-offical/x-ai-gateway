# REQ-20260517-004 OpenAI Vector Store Files 本地 Attachment Lifecycle 基线

状态：Done
日期：2026-05-17
来源任务：[TASK-20260514-023](../../tasks/backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 背景

`TASK-20260517-003` 已补齐 `/v1/vector_stores` create/list/retrieve/update/delete，但官方 Vector Store Files 还包含 attach/list/retrieve/delete/content 等子资源。为继续推进 `TASK-20260514-023`，本轮先建立本地 attachment lifecycle，让客户能把 file id 关联到本地 vector store，并获得可分页、可删除的 `vector_store.file` 对象。

## 目标

- 增加 `/v1/vector_stores/{vectorStoreId}/files` create/list。
- 增加 `/v1/vector_stores/{vectorStoreId}/files/{fileId}` retrieve/delete。
- 使用 `gateway_async_resource` 保存当前 Distributed Key 下的本地 vector store file attachment。
- create 支持 `file_id`、`attributes`、`chunking_strategy`，并更新 parent vector store 的 `file_counts`。
- list 支持 `after`、`limit`、`order`、`filter`，返回 OpenAI-compatible list envelope。
- delete 软删除 attachment，返回 `object=vector_store.file.deleted` 与 `deleted=true`。
- 更新 public docs、OpenAPI、provider catalog、父任务和测试。

## 非目标

- 不实现 `/files/{fileId}/content` 内容读取。
- 不实现 `/file_batches`。
- 不实现 vector store `search`。
- 不让 Responses `file_search` tool 真执行。
- 不访问真实 OpenAI。

## 方案

1. `GatewayAsyncResourceType` 新增 `VECTOR_STORE_FILE`。
2. `GatewayAsyncResourceService` 新增 vector store file create/list/get/delete 方法，parent 使用 `upstreamObjectId=vectorStoreId`。
3. attachment payload 使用 OpenAI-compatible `object=vector_store.file`，`id` 保持传入的 file id，便于客户端按官方路径 retrieve/delete。
4. 同一 Distributed Key、同一 vector store、同一 file id 只允许一个 active attachment。
5. docs/openapi/public docs/provider catalog 增加 `openai.vector-store-files-local-attachment` conformance。

## 影响范围

- `GatewayAsyncResourceType`
- `GatewayAsyncResourceService`
- `OpenAiVectorStoresController`
- Controller/service tests
- `PublicDocsBundleService`
- `docs/openapi/public-openapi.json`
- `provider-catalog.json`
- `tasks/backlog/TASK-20260514-023-openai-vector-stores-full-stack.md`
- OpenAI 覆盖报告

## 风险

- 本地 attachment 不能被误读为真实 OpenAI ingestion 或可检索索引完成。
- 同一 file id 可能被挂到多个 vector store；存储层不能只用 file id 做全局唯一判断。
- parent vector store 的 `file_counts` 必须在 create/delete 后保持一致。

## 验收标准

- `POST /v1/vector_stores/{vectorStoreId}/files` 可创建本地 attachment。
- 重复 attach 同一 vector store 的同一 file id 会被拒绝。
- `GET /v1/vector_stores/{vectorStoreId}/files` 支持 list envelope、分页参数和 status filter。
- `GET/DELETE /v1/vector_stores/{vectorStoreId}/files/{fileId}` 支持 retrieve/delete。
- Public docs、OpenAPI、provider catalog 和父任务已同步当前边界。

## 测试边界

- `OpenAiVectorStoreFilesControllerTests`
- `GatewayAsyncResourceVectorStoreFilesTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`

## 实现结果

- 新增 `GatewayAsyncResourceType.VECTOR_STORE_FILE`。
- `GatewayAsyncResourceService` 增加 vector store file create/list/retrieve/delete。
- `OpenAiVectorStoresController` 增加：
  - `POST /v1/vector_stores/{vectorStoreId}/files`
  - `GET /v1/vector_stores/{vectorStoreId}/files`
  - `GET /v1/vector_stores/{vectorStoreId}/files/{fileId}`
  - `DELETE /v1/vector_stores/{vectorStoreId}/files/{fileId}`
- 同一 Distributed Key、同一 vector store、同一 `file_id` 只允许一个 active attachment；同一 `file_id` 可挂到不同 vector store。
- create/delete 会同步 parent vector store 的 `file_counts` 和 `last_active_at`。
- `attributes` 支持字符串、数字、布尔值或 null；`chunking_strategy` 做本地保真保存，缺省为 `auto`。
- Public docs、OpenAPI snapshot、provider catalog、覆盖审计报告和父任务已同步 `openai.vector-store-files-local-attachment`。

## 验证情况

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoresTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreFilesTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoresControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoreFilesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

结果：通过。

## 遗留问题

- `/files/{fileId}/content` 仍未实现。
- `/file_batches` 仍未实现。
- Vector Store `search` 与真实向量检索仍未实现。
- Responses `file_search` tool 仍保持显式拒绝，不能声明已支持真实检索。
- 真实 OpenAI Direct Vector Store Files smoke 仍归属 `TASK-20260514-023` 后续切片。
