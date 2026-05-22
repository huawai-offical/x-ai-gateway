# REQ-20260518-002 OpenAI Vector Store Search 本地文本检索基线

状态：Done
日期：2026-05-18
上游来源：[TASK-20260514-023](../../tasks/done/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 背景

`TASK-20260517-003` 已补 Vector Stores 本地 lifecycle，`TASK-20260517-004` 已补 Vector Store Files attachment lifecycle，`TASK-20260517-005` 已补 File Batches lifecycle，`TASK-20260518-001` 已补 Vector Store File Content 本地读取。父任务仍遗留 Vector Store `search`、真实向量检索、Responses `file_search` tool resource binding 和真实 smoke。

官方 Vector Store Search 入口是 `POST /v1/vector_stores/{vectorStoreId}/search`，请求包含 `query`、可选 `filters`、`max_num_results`、`ranking_options`、`rewrite_query`，返回 `vector_store.search_results.page`。当前项目已有本地 attachment 和 file content 读取能力，可以先建立可测试的本地文本检索基线，缩小公开路径缺口。

## 目标

- 新增 `POST /v1/vector_stores/{vectorStoreId}/search`。
- 仅检索当前 Distributed Key 下、指定 Vector Store 内 active `vector_store.file` attachment 对应的可读取 gateway file 文本内容。
- 支持官方形状的核心请求字段：
  - `query`：必填，支持 string 或 string array。
  - `filters`：支持基于 attachment `attributes` 的 `eq/ne/gt/gte/lt/lte/in/nin` 和 `and/or` 组合。
  - `max_num_results`：默认 10，范围 1 到 50。
  - `ranking_options.score_threshold`：范围 0 到 1，本地用于过滤词法匹配分数。
  - `ranking_options.ranker` 与 `rewrite_query`：本地接受并保真到响应，不执行上游 rerank 或 query rewrite。
- 返回 OpenAI-compatible `vector_store.search_results.page`：
  - `object=vector_store.search_results.page`
  - `search_query`
  - `data[]` 包含 `file_id`、`filename`、`score`、`attributes`、`content[]`
  - `has_more` 与 `next_page`
- 同步 public docs、OpenAPI snapshot、provider catalog、父任务和测试。

## 范围

- `GatewayAsyncResourceService`
- `OpenAiVectorStoresController`
- Public API docs、OpenAPI、provider catalog
- Controller/service/docs/provider catalog 测试

## 非目标

- 不实现 embedding、ANN、rerank 或真实 vector ingestion。
- 不执行 Responses `file_search` tool。
- 不访问真实 OpenAI 或创建上游托管 Vector Store。
- 不对不可读取的 attachment 强行联网解析；本地 search 只返回可读取 gateway file 的结果。
- 不把 `ranking_options.ranker` 或 `rewrite_query` 解释成真实上游行为。

## 方案

- 在 `GatewayAsyncResourceService` 新增 `searchVectorStore(vectorStoreId, distributedKeyId, requestBody)`。
- 先校验 parent `VECTOR_STORE`，再读取当前 vector store 下 active `VECTOR_STORE_FILE` attachment。
- 对每个 attachment：
  - 使用 attachment payload 的 `attributes` 执行本地 filter。
  - 尝试读取 gateway file content；读取失败或不属于当前 Distributed Key 的 attachment 不参与本地搜索结果。
  - 按 UTF-8 文本做大小写不敏感词法匹配，并返回确定性 score。
- 排序按 `score desc`，同分按 `file_id asc`。
- `data[].content[]` 返回单条文本片段；本地基线不做真实 chunking 或 token 分页。

## 风险

- 本地词法搜索只能用于路径兼容和基础可测场景，不能等价于 OpenAI 托管语义向量检索。
- 早期 attachment 可能引用上游 file id 而非本地 gateway file；这些结果应被安全跳过，避免破坏搜索请求。
- filter 需要保持清晰错误，避免 silently ignore 导致客户误判。

## 验收标准

- `POST /v1/vector_stores/{vectorStoreId}/search` controller 测试通过。
- service 测试覆盖 query string、query array、attributes filter、`max_num_results`、`score_threshold` 和非法输入。
- 搜索结果只返回当前 Distributed Key 和 parent vector store 下的 active attachment。
- Public docs、OpenAPI、provider catalog 和父任务均明确 search 本地文本基线已支持，真实向量入库/Responses `file_search` 仍未完成。

## 测试边界

- `GatewayAsyncResourceVectorStoreSearchTests`
- `OpenAiVectorStoreSearchControllerTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`

## 关联文档

- [TASK-20260514-023](../../tasks/done/TASK-20260514-023-openai-vector-stores-full-stack.md)
- OpenAI Vector Store Search API Reference：
  - https://developers.openai.com/api/reference/resources/vector_stores/methods/search
  - https://platform.openai.com/docs/api-reference/vector-stores/search

## 实现结果

- `GatewayAsyncResourceService.searchVectorStore` 已按当前 Distributed Key、parent `VECTOR_STORE` 和 active `VECTOR_STORE_FILE` attachment 边界执行本地检索。
- `OpenAiVectorStoresController` 已暴露 `POST /v1/vector_stores/{vectorStoreId}/search`。
- 本地 search 支持 string/string array `query`、attributes `filters`、`max_num_results`、`ranking_options.score_threshold`，并保留 `ranking_options` 与 `rewrite_query` 响应字段。
- 返回 `object=vector_store.search_results.page`、`search_query`、`data[].file_id`、`filename`、`score`、`attributes`、`content[]`、`has_more` 与 `next_page`。
- Public docs、OpenAPI snapshot、provider catalog、兼容性报告和父任务已同步 `openai.vector-store-search-local-text`。

## 验证情况

- 通过：
  - `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreSearchTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoreSearchControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"`

## 遗留问题

- 本轮仅实现 deterministic UTF-8 lexical baseline，不等价于 OpenAI 托管 embedding、semantic vector search、rerank 或 query rewrite。
- Responses `file_search` tool resource binding、真实向量入库和真实 OpenAI smoke 仍归属 `TASK-20260514-023` 后续切片。

## 当前状态

- 2026-05-18：需求建档，进入实现。
- 2026-05-18：实现、公开文档、provider catalog 与 targeted tests 已完成，状态更新为 Done。
