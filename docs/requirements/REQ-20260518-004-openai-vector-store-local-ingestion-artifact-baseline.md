# REQ-20260518-004 OpenAI Vector Store 本地 Ingestion 产物基线

状态：Done
日期：2026-05-18
上游来源：[TASK-20260514-023](../../tasks/backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 背景

`TASK-20260518-001` 已提供 Vector Store File Content 本地读取，`TASK-20260518-002` 已提供本地文本 search，`TASK-20260518-003` 已让 Responses `file_search` 可以绑定本地 `vector_store_ids` 并注入检索上下文。但当前 search 仍在请求时读取原始 gateway file 文本并即时做词法打分，Vector Store File attachment 本身没有固定 ingestion 产物，也没有记录 chunk、hash、ingestion 状态或 usage bytes。

父任务剩余切片包含真实向量入库、语义向量检索、hosted `file_search_call` lifecycle 与真实 smoke。本轮先把本地 ingestion 产物固化到 `VECTOR_STORE_FILE` payload/metadata：attachment 创建时读取当前 Distributed Key 下的文件内容，按 `chunking_strategy` 生成可复用 chunk 列表，记录字符/字节/token 估算、content hash 和 local index 版本。这样后续语义检索或 hosted call lifecycle 可以基于稳定的本地 ingestion 证据推进，而不是继续依赖临时读取原文。

## 目标

- Vector Store File attachment 创建时生成本地 ingestion 产物。
- `createVectorStore` 携带 `file_ids`、`createVectorStoreFile`、`createVectorStoreFileBatch` 三条路径统一复用同一 ingestion 逻辑。
- `VECTOR_STORE_FILE` response payload 包含：
  - `usage_bytes` 使用真实 gateway file size。
  - `status=completed` 时带 `last_error=null`。
  - `chunking_strategy` 经过本地归一化。
- `VECTOR_STORE_FILE` metadata 包含：
  - `ingestion.object_mode=gateway_vector_store_file_ingestion`
  - `ingestion.status=completed`
  - `ingestion.index_version`
  - `ingestion.file_id`
  - `ingestion.filename`
  - `ingestion.content_sha256`
  - `ingestion.bytes`
  - `ingestion.estimated_tokens`
  - `ingestion.chunk_count`
  - `ingestion.chunks[]`
- 本地 search 优先读取 ingestion chunks；没有 ingestion metadata 的旧 attachment 再回退读取原始 file content。
- 无法读取文件或跨租户文件时，attachment 创建直接失败，避免生成“completed 但不可检索”的假对象。
- 更新 docs、provider catalog、父任务、测试与任务索引。

## 范围

- `GatewayAsyncResourceService`
- Vector Store Files / File Batches service tests
- Vector Store Search service tests
- Public docs、provider catalog、报告与任务状态

## 非目标

- 不调用 OpenAI Embeddings 或第三方 embedding provider。
- 不实现 ANN/vector database。
- 不实现 semantic rerank、query rewrite 或 hosted `file_search_call` 输出生命周期。
- 不改变 OpenAI Direct 上游同步边界。

## 方案

- 在 `saveVectorStoreFileAttachment` 内解析当前文件内容并生成 `LocalVectorStoreIngestion`。
- 支持 `chunking_strategy.type=auto` 与 `static` 的基础 chunk 参数归一化：
  - `auto` 使用本地默认 chunk size 与 overlap。
  - `static` 读取 `max_chunk_size_tokens` 与 `chunk_overlap_tokens`，并校验范围。
- 采用确定性本地 chunk：以字符近似 token，将文本切成稳定 chunk，记录 `chunk_id`、`index`、`text`、`start_char`、`end_char`、`estimated_tokens`。
- search 阶段优先使用 metadata 中的 chunk 文本打分，并保留旧 payload 回退，保证历史 attachment 不立即失效。

## 风险

- 本地 token 估算不等价于 OpenAI tokenizer，必须在文档中明确。
- 本地 chunk 不等价于 OpenAI hosted parsing/embedding，不能对外宣称 semantic retrieval 已完成。
- `metadata_json` 可能变大；本轮只适合本地基线和测试规模，后续真实向量索引仍需独立存储设计。

## 验收标准

- 创建 Vector Store File 时，payload 的 `usage_bytes` 与本地 gateway file size 一致。
- metadata 中包含稳定 ingestion 产物、chunk_count 与 chunks。
- file batch 与 vector store create-with-files 路径也生成 ingestion metadata。
- search 优先使用 metadata chunks，并能在旧 metadata 缺失时回退原始文件读取。
- 无法读取或跨租户文件不会生成 completed attachment。
- docs、provider catalog、父任务和任务索引同步。

## 测试边界

- `GatewayAsyncResourceVectorStoreFilesTests`
- `GatewayAsyncResourceVectorStoreFileBatchesTests`
- `GatewayAsyncResourceVectorStoreSearchTests`
- `ProviderCatalogLoaderTests`
- `PublicDocsBundleServiceTests`

## 关联文档

- [TASK-20260514-023](../../tasks/backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)
- [REQ-20260518-001](REQ-20260518-001-openai-vector-store-file-content-local-read-baseline.md)
- [REQ-20260518-002](REQ-20260518-002-openai-vector-store-search-local-text-baseline.md)
- [REQ-20260518-003](REQ-20260518-003-openai-responses-file-search-local-vector-store-binding.md)

## 实现结果

- `GatewayAsyncResourceService.saveVectorStoreFileAttachment` 现在在创建 attachment 时读取当前 Distributed Key 下的 gateway file，并在 `VECTOR_STORE_FILE.metadata_json.ingestion` 写入本地 ingestion 产物。
- `usage_bytes` 改为真实文件字节数；无法读取文件或 file 不属于当前 Distributed Key 时，attachment 创建直接失败。
- 本地 ingestion metadata 包含 `object_mode`、`status`、`index_version=local-chunk-v1`、`file_id`、`filename`、`content_sha256`、`bytes`、`estimated_tokens`、`chunk_count`、chunk 参数与 `chunks[]`。
- `createVectorStore(file_ids)`、`createVectorStoreFile`、`createVectorStoreFileBatch` 三条路径复用同一 ingestion 逻辑。
- `searchVectorStore` 优先检索已固化的 ingestion chunks；旧 attachment 缺少 ingestion metadata 时继续回退读取原始 gateway file 文本。
- Public docs、OpenAPI snapshot、provider catalog 和 conformance checks 已补充 `openai.vector-store-files-local-ingestion-artifact` 能力边界。

## 验证结果

通过命令：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoresTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreFilesTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreFileBatchesTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreSearchTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

## 遗留问题

- 本地 token 估算仍是字符近似，不等价于 OpenAI tokenizer。
- chunk 文本暂存在 `metadata_json`，适合基线和可追踪证据；真实向量索引仍需要独立存储与清理策略。
- 真实 embedding/vector index ingestion、语义检索、hosted `file_search_call` lifecycle 与真实 smoke 继续归属父任务后续切片。

## 当前状态

- 2026-05-18：已完成实现、测试和公开文档/catalog 回写。
