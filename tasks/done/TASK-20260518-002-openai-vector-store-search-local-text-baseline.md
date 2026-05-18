# TASK-20260518-002 OpenAI Vector Store Search 本地文本检索基线

状态：Done
优先级：High
类型：子任务
父任务：[TASK-20260514-023](../backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)
上游来源：[REQ-20260518-002](../../docs/requirements/REQ-20260518-002-openai-vector-store-search-local-text-baseline.md)

## 背景

Vector Stores、Files、File Batches 和 File Content 已建立本地基线，但 `/v1/vector_stores/{vectorStoreId}/search` 仍缺入口。下一步先做本地文本检索基线，让当前 gateway-local vector store 可以被查询，同时继续保留真实向量入库和 Responses `file_search` tool 为后续切片。

## 目标

- 新增 OpenAI-compatible Vector Store Search endpoint。
- 基于当前 Distributed Key、parent vector store 和 active attachment 边界搜索可读取 gateway file 文本。
- 支持 `query`、`filters`、`max_num_results`、`ranking_options.score_threshold`。
- 同步 public docs、OpenAPI、provider catalog 和父任务。

## 非目标

- 不实现真实向量检索、embedding、rerank 或 chunking。
- 不执行 Responses `file_search` tool。
- 不访问真实 OpenAI。
- 不对无法读取的 attachment 生成虚假搜索结果。

## 输入

- `GatewayAsyncResourceService`
- `OpenAiVectorStoresController`
- `GatewayFileService`
- `TASK-20260514-023`
- OpenAI Vector Store Search API Reference

## 输出

- `searchVectorStore` service method。
- Controller endpoint 与测试。
- Public docs/OpenAPI/provider catalog 更新。
- 父任务剩余切片更新。

## 影响范围

- OpenAI ingress。
- Async resource storage。
- Gateway file content 读取。
- Public compatibility docs 和 OpenAPI。
- Provider catalog conformance。

## 依赖

- [TASK-20260517-003](../done/TASK-20260517-003-openai-vector-stores-local-lifecycle-baseline.md)
- [TASK-20260517-004](../done/TASK-20260517-004-openai-vector-store-files-local-attachment-lifecycle.md)
- [TASK-20260517-005](../done/TASK-20260517-005-openai-vector-store-file-batches-local-lifecycle.md)
- [TASK-20260518-001](../done/TASK-20260518-001-openai-vector-store-file-content-local-read-baseline.md)

## 风险

- 本地词法搜索容易被误解为 OpenAI 托管语义搜索，需要在 docs/catalog 中明确边界。
- attachment 可能没有可读取本地文件，不能让搜索泄露或崩溃。
- filters shape 若不校验会导致兼容行为不可预测。

## 验收标准

- Controller 暴露 `POST /v1/vector_stores/{vectorStoreId}/search`。
- Service 返回 `object=vector_store.search_results.page`，结果包含 `file_id`、`filename`、`score`、`attributes`、`content`。
- attributes filters、score threshold 和 max results 行为可测。
- Public docs、OpenAPI、provider catalog 与父任务状态同步。

## 测试边界

- `GatewayAsyncResourceVectorStoreSearchTests`
- `OpenAiVectorStoreSearchControllerTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`

## 关联文档

- [REQ-20260518-002](../../docs/requirements/REQ-20260518-002-openai-vector-store-search-local-text-baseline.md)
- [TASK-20260514-023](../backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 实现结果

- `GatewayAsyncResourceService` 新增 `searchVectorStore`，只搜索当前 Distributed Key 与 parent vector store 下的 active attachment。
- `OpenAiVectorStoresController` 新增 `POST /v1/vector_stores/{vectorStoreId}/search`。
- attributes filter 支持 `eq/ne/gt/gte/lt/lte/in/nin` 与 `and/or`，非法 filter shape 会直接返回清晰错误；缺失数值属性不会让搜索崩溃，只是不匹配。
- 本地检索返回 `vector_store.search_results.page`，按 `score desc, file_id asc` 排序，并支持 `max_num_results`、`score_threshold`、`has_more` 与 `next_page`。
- Public docs、OpenAPI snapshot、provider catalog、兼容性报告、docs index 和父任务均已同步。

## 验证结果

- 通过：
  - `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreSearchTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoreSearchControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"`

## 遗留问题

- 真实向量入库、语义检索、rerank/query rewrite 和 Responses `file_search` tool resource binding 不在本任务范围，继续留在父任务后续切片。

## 当前状态

- 2026-05-18：已建档，准备实现。
- 2026-05-18：实现与 targeted tests 完成，任务归档为 Done。
