# REQ-20260518-001 OpenAI Vector Store File Content 本地读取基线

状态：Done
日期：2026-05-18
上游来源：[TASK-20260514-023](../../tasks/done/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 背景

`TASK-20260517-003` 已补 Vector Stores 本地 lifecycle，`TASK-20260517-004` 已补 Vector Store Files attachment lifecycle，`TASK-20260517-005` 已补 File Batches lifecycle。父任务仍遗留 Vector Store File Content、search、真实向量入库、Responses `file_search` tool resource binding 和真实 smoke。

官方 Vector Store File Content 入口用于读取已关联文件的解析内容页。当前项目已有 `/v1/files/{fileId}/content` 的文件字节读取能力，以及 `vector_store.file` attachment，但还没有 `/v1/vector_stores/{vectorStoreId}/files/{fileId}/content`，导致 SDK 或兼容客户端无法从 vector store attachment 路径读取内容。

## 目标

- 新增 `GET /v1/vector_stores/{vectorStoreId}/files/{fileId}/content`。
- 读取当前 Distributed Key 下、指定 Vector Store 内 active `vector_store.file` attachment 对应的 gateway file 内容。
- 返回 OpenAI-compatible `vector_store.file_content.page` JSON：
  - `object=vector_store.file_content.page`
  - `data=[{"type":"text","text":"..."}]`
  - `content` 作为 `data` 的兼容别名
  - `has_more=false`
  - `next_page=null`
  - 同时保留 `file_id`、`filename`、`attributes` 方便对齐官方示例字段
- 保持 parent vector store 与 file attachment 的双重租户校验。
- 同步 public docs、OpenAPI snapshot、provider catalog、父任务和测试。

## 范围

- `GatewayAsyncResourceService`
- `OpenAiVectorStoresController`
- Public API docs、OpenAPI、provider catalog
- Controller/service/docs/provider catalog 测试

## 非目标

- 不实现 Vector Store search。
- 不实现真实向量索引、embedding、chunking 或异步 ingestion worker。
- 不让 Responses `file_search` tool 真执行。
- 不访问真实 OpenAI 或创建上游托管 Vector Store。
- 不改变 `/v1/files/{fileId}/content` 的 binary 返回行为。

## 方案

- 在 `GatewayAsyncResourceService` 新增 `getVectorStoreFileContent(vectorStoreId, fileId, distributedKeyId)`。
- 先读取并校验 parent `VECTOR_STORE`，再通过现有 `getRequiredVectorStoreFile` 确认 attachment 属于该 vector store。
- 复用 `GatewayFileService.getFileContent`；测试或旧构造路径下如未注入 `GatewayFileService`，回落到当前类已有的本地 `getGatewayFileContent`。
- 将文件字节按 UTF-8 转为单页文本内容；本地 baseline 不做 OCR、PDF 分页、chunking 或 token 切片。
- 输出中 `attributes` 取自 attachment payload，`filename` 取自文件 metadata。

## 风险

- 非文本文件按 UTF-8 转为文本可能只适合作为本地 baseline，不能等价于 OpenAI 托管解析结果。
- 官方文档的 Returns schema 与示例字段存在差异，本轮需要用兼容别名减少客户端差异风险。
- 如果 attachment 指向的 gateway file 已删除或跨租户，应返回清晰错误，不能泄露内容。

## 验收标准

- `GET /v1/vector_stores/{vectorStoreId}/files/{fileId}/content` controller 测试通过。
- service 测试覆盖成功读取、attachment 不存在、gateway file 不属于当前 Distributed Key。
- 返回 JSON 包含 `object=vector_store.file_content.page`、`data[0].type=text`、`data[0].text`、`content[0].text`、`has_more=false`。
- Public docs、OpenAPI、provider catalog 和父任务均明确 file content 本地读取已支持，search/真实 ingestion/Responses `file_search` 仍未完成。

## 测试边界

- `GatewayAsyncResourceVectorStoreFilesTests`
- `OpenAiVectorStoreFilesControllerTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`

## 关联文档

- [TASK-20260514-023](../../tasks/done/TASK-20260514-023-openai-vector-stores-full-stack.md)
- OpenAI Vector Store File Content API Reference：
  - https://developers.openai.com/api/reference/resources/vector_stores/subresources/files/methods/content
  - https://platform.openai.com/docs/api-reference/vector-stores-files/getFileContent

## 实现结果

- `GatewayAsyncResourceService` 新增 `getVectorStoreFileContent`，按当前 Distributed Key、parent `VECTOR_STORE` 与 active `VECTOR_STORE_FILE` attachment 校验后读取对应 gateway file。
- 文件内容通过 `GatewayFileService.getFileContent` 复用现有 file content 能力；测试/旧构造路径下回退到本地 `GatewayFileRepository` 与 storage path 读取。
- `OpenAiVectorStoresController` 新增 `GET /v1/vector_stores/{vectorStoreId}/files/{fileId}/content`。
- 返回 `object=vector_store.file_content.page`、`file_id`、`filename`、`attributes`、`data`、`content`、`has_more=false` 与 `next_page=null`。
- Public docs、OpenAPI snapshot、provider catalog、审计报告和父任务已同步 `openai.vector-store-file-content-local-read`。

## 验证情况

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreFilesTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoreFilesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

结果：通过。

## 遗留问题

- Vector Store search、真实向量索引、Responses `file_search` tool resource binding 和真实 OpenAI Direct smoke 仍属于 `TASK-20260514-023` 后续切片。
- 本轮没有访问真实 OpenAI Vector Store File Content API，不把本地 UTF-8 文本读取解释为 OpenAI 托管解析、embedding 或真实向量索引完成。

## 当前状态

- 2026-05-18：已完成并归档。
