# REQ-20260517-003 OpenAI Vector Stores 本地 Lifecycle 基线

状态：Done
日期：2026-05-17
来源任务：[TASK-20260514-023](../../tasks/done/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 背景

官方 OpenAI Vector Stores API 暴露 vector store create/list/retrieve/update/delete/search，以及 vector store files/file batches 等子资源。当前项目只有 OpenAI path matcher 与真实 smoke 里提到 `/v1/vector_stores`，没有公开 controller 或本地对象 lifecycle，导致 `TASK-20260514-023` 仍是“无公开入口”的状态。

## 目标

- 增加 `/v1/vector_stores` 本地 lifecycle 基线：create、list、retrieve、update、delete。
- 返回 OpenAI-compatible `vector_store` 对象，包含 `id`、`object`、`created_at`、`name`、`metadata`、`status`、`usage_bytes`、`file_counts` 等基础字段。
- list 使用 OpenAI-compatible list envelope，支持 `after`、`limit`、`order`。
- update 支持 `name`、`metadata`、`expires_after` 等对象元数据更新。
- delete 返回 `object=vector_store.deleted` 与 `deleted=true`，并按 Distributed Key 隔离。
- 更新 public docs、OpenAPI、provider catalog、父任务和测试。

## 非目标

- 不实现 OpenAI 托管向量检索结果；`search`、`files`、`file_batches` 后续继续拆分。
- 不把 Responses `file_search` tool 从 rejected 改为 supported。
- 不连接真实 OpenAI Vector Stores；本任务只做本地 lineage lifecycle。
- 不自研向量数据库。

## 方案

1. `GatewayAsyncResourceType` 新增 `VECTOR_STORE`。
2. `GatewayAsyncResourceService` 新增 vector store create/list/get/update/delete 方法，复用 `gateway_async_resource` 本地资源表。
3. 新增 `OpenAiVectorStoresController`，复用 Distributed Key 鉴权。
4. list 使用已有 `findStoredResourcesAfterCursorAsc/Desc` cursor 查询，resource key 前缀为 `vs_`。
5. docs/openapi/public docs/provider catalog 增加 `openai.vector-stores-local-lifecycle` conformance。

## 影响范围

- `GatewayAsyncResourceType`
- `GatewayAsyncResourceService`
- `OpenAiVectorStoresController`
- Controller/service tests
- `PublicDocsBundleService`
- `docs/openapi/public-openapi.json`
- `provider-catalog.json`
- `tasks/done/TASK-20260514-023-openai-vector-stores-full-stack.md`
- OpenAI 覆盖报告

## 风险

- 本地 lifecycle 不能被误读为 OpenAI 托管检索完成；docs 和 provider catalog 必须保留 search/files/file_batches 未完成声明。
- Vector store delete 必须按 Distributed Key 隔离，不能删除其他租户对象。
- 如果 `file_ids` 被传入，只能作为本地引用基线保存，不能声明真实 ingestion。

## 验收标准

- `POST /v1/vector_stores` 可创建本地 vector store。
- `GET /v1/vector_stores` 支持 list envelope、分页参数和租户隔离。
- `GET/POST/DELETE /v1/vector_stores/{vectorStoreId}` 支持 retrieve/update/delete。
- 删除后同 key retrieve 返回 not found。
- Public docs、OpenAPI、provider catalog 和父任务已同步当前边界。
- 聚焦测试通过。

## 测试边界

- `OpenAiVectorStoresControllerTests`
- `GatewayAsyncResourceVectorStoresTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`

## 实现结果

- 新增 `GatewayAsyncResourceType.VECTOR_STORE`，并在 `GatewayAsyncResourceService` 中实现 create/list/retrieve/update/delete。
- 新增 `OpenAiVectorStoresController`，公开：
  - `POST /v1/vector_stores`
  - `GET /v1/vector_stores`
  - `GET /v1/vector_stores/{vectorStoreId}`
  - `POST /v1/vector_stores/{vectorStoreId}`
  - `DELETE /v1/vector_stores/{vectorStoreId}`
- Vector Store 本地对象使用 `vs_` 前缀，保存为 `gateway_async_resource` 的 `VECTOR_STORE`，按 Distributed Key 隔离，删除为软删除。
- list 复用数据库 cursor 查询，支持 `after`、`limit`、`order`，返回 OpenAI-compatible list envelope。
- create/update 返回 `vector_store` shape，并保留 `name`、`metadata`、`expires_after`、`expires_at`、`usage_bytes` 和 `file_counts`。
- Public docs、OpenAPI snapshot、provider catalog、覆盖审计报告和父任务已同步 `openai.vector-stores-local-lifecycle`。

## 验证情况

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoresTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoresControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

结果：通过。

## 遗留问题

- Vector Store `search` 仍未实现。
- `/v1/vector_stores/{vectorStoreId}/files` 与 `/file_batches` 子资源仍未实现。
- Responses `file_search` tool 仍保持显式拒绝，不能声明已支持真实检索。
- 真实 OpenAI Direct Vector Stores smoke 仍归属 `TASK-20260514-023` 后续切片。
