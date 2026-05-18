# TASK-20260518-001 OpenAI Vector Store File Content 本地读取基线

状态：Done
优先级：High
类型：子任务
父任务：[TASK-20260514-023](../backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)
上游来源：[REQ-20260518-001](../../docs/requirements/REQ-20260518-001-openai-vector-store-file-content-local-read-baseline.md)

## 背景

Vector Store Files attachment 与 File Batches 已完成本地 lifecycle，但 attachment 路径下仍不能读取文件解析内容。官方 Vector Store File Content 入口是 `GET /v1/vector_stores/{vectorStoreId}/files/{fileId}/content`，返回 `vector_store.file_content.page` 结构。

## 目标

- 新增 OpenAI-compatible Vector Store File Content 本地读取 endpoint。
- 基于当前 Distributed Key、parent vector store 和 active attachment 三重边界读取 gateway file 内容。
- 返回文本页 JSON，并保留官方 Returns schema 与示例字段的兼容性。
- 同步 public docs、OpenAPI、provider catalog 和父任务。

## 非目标

- 不实现 search。
- 不实现真实向量入库、chunking、embedding 或 OCR。
- 不执行 Responses `file_search` tool。
- 不访问真实 OpenAI。

## 输入

- `GatewayAsyncResourceService`
- `OpenAiVectorStoresController`
- `GatewayFileService`
- `TASK-20260514-023`
- OpenAI Vector Store File Content API Reference

## 输出

- `getVectorStoreFileContent` service method。
- Controller endpoint 与测试。
- Public docs/OpenAPI/provider catalog 更新。
- 父任务剩余切片更新。

## 影响范围

- OpenAI ingress。
- Async resource storage。
- Gateway file content读取。
- Public compatibility docs 和 OpenAPI。
- Provider catalog conformance。

## 依赖

- [TASK-20260517-003](../done/TASK-20260517-003-openai-vector-stores-local-lifecycle-baseline.md)
- [TASK-20260517-004](../done/TASK-20260517-004-openai-vector-store-files-local-attachment-lifecycle.md)
- [TASK-20260517-005](../done/TASK-20260517-005-openai-vector-store-file-batches-local-lifecycle.md)

## 风险

- 本地文本页读取不能被误解为 OpenAI 托管解析或真实向量索引。
- attachment 存在但 gateway file 已删除时必须返回明确错误。
- 不能绕过 Distributed Key 边界读取其他租户文件。

## 验收标准

- Controller 暴露 `GET /v1/vector_stores/{vectorStoreId}/files/{fileId}/content`。
- Service 成功返回 `object=vector_store.file_content.page` 和 text 内容页。
- 跨租户或缺失 file content 不泄露内容。
- Public docs、OpenAPI、provider catalog 与父任务状态同步。

## 测试边界

- `GatewayAsyncResourceVectorStoreFilesTests`
- `OpenAiVectorStoreFilesControllerTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`

## 关联文档

- [REQ-20260518-001](../../docs/requirements/REQ-20260518-001-openai-vector-store-file-content-local-read-baseline.md)
- [TASK-20260514-023](../backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 实现结果

- `GatewayAsyncResourceService` 增加 `getVectorStoreFileContent`，先校验 parent vector store，再校验 active attachment，最后读取对应 gateway file。
- `OpenAiVectorStoresController` 增加 `GET /v1/vector_stores/{vectorStoreId}/files/{fileId}/content`。
- 本地返回 `vector_store.file_content.page` 文本页，兼容 `data` 与 `content` 两个文本数组字段，并返回 `file_id`、`filename`、`attributes`、`has_more=false`、`next_page=null`。
- Service 测试覆盖成功读取、缺失 attachment、跨 Distributed Key 文件拒绝。
- Controller 测试覆盖 content endpoint 响应结构。
- Public docs、OpenAPI snapshot、provider catalog、审计报告和父任务已同步当前边界。

## 验证情况

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreFilesTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoreFilesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

结果：通过。

## 遗留问题

- Search、真实向量入库、Responses `file_search` tool 和真实 smoke 仍留在父任务后续切片。
- 本地文本页读取不等价于 OpenAI 托管解析或真实 vector ingestion。

## 当前状态

- 2026-05-18：已完成并归档。
