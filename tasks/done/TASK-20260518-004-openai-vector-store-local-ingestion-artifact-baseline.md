# TASK-20260518-004 OpenAI Vector Store 本地 Ingestion 产物基线

状态：Done
优先级：High
类型：子任务
父任务：[TASK-20260514-023](../backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)
上游来源：[REQ-20260518-004](../../docs/requirements/REQ-20260518-004-openai-vector-store-local-ingestion-artifact-baseline.md)

## 背景

Vector Stores 本地 lifecycle、file attachment、file content、text search 与 Responses file_search binding 已闭环，但 attachment 创建时没有固化 ingestion 产物。当前 search 每次临时读取原始 gateway file，缺少可追踪的 chunk/index evidence。

## 目标

- 创建 Vector Store File attachment 时生成本地 ingestion metadata。
- create-with-files、single file attach、file batch 三条路径复用同一 ingestion 逻辑。
- 记录 chunk、content hash、usage_bytes、estimated tokens、chunk_count 与 index version。
- search 优先使用 ingestion chunks，缺失时兼容旧 attachment 回退原文读取。
- 同步 docs、provider catalog、父任务和测试。

## 非目标

- 不调用真实 embedding provider。
- 不实现语义向量召回或 ANN 索引。
- 不实现 hosted `file_search_call` lifecycle。
- 不做 OpenAI Direct 上游同步。

## 输入

- `GatewayAsyncResourceService.saveVectorStoreFileAttachment`
- `GatewayAsyncResourceService.searchVectorStore`
- `GatewayFileRepository`
- `TASK-20260514-023`

## 输出

- 本地 ingestion metadata 与 payload usage_bytes。
- chunk-aware local search fallback。
- service tests 与 public docs/catalog 更新。

## 影响范围

- Vector Store File attachment 创建。
- Vector Store File Batch 创建。
- Vector Store Search。
- Public compatibility docs 与 provider catalog。

## 依赖

- [TASK-20260518-001](../done/TASK-20260518-001-openai-vector-store-file-content-local-read-baseline.md)
- [TASK-20260518-002](../done/TASK-20260518-002-openai-vector-store-search-local-text-baseline.md)
- [TASK-20260518-003](../done/TASK-20260518-003-openai-responses-file-search-local-vector-store-binding.md)

## 风险

- metadata_json 承载 chunk 内容会增大；本轮仅作为本地基线，后续真实向量索引需要拆独立存储。
- token 估算是本地近似，不能冒充 OpenAI tokenizer。
- chunk search 仍不是 semantic retrieval。

## 验收标准

- attachment 创建后 metadata 中有 `ingestion` 对象与 chunks。
- payload `usage_bytes` 使用本地文件真实字节数。
- file batch 和 create-with-files 也会生成 ingestion metadata。
- search 优先使用 ingestion chunks，旧 attachment 可回退读取文件。
- 无法读取或跨租户 file_id 创建失败。
- 文档、provider catalog 和父任务同步。

## 测试边界

- `GatewayAsyncResourceVectorStoreFilesTests`
- `GatewayAsyncResourceVectorStoreFileBatchesTests`
- `GatewayAsyncResourceVectorStoreSearchTests`
- `ProviderCatalogLoaderTests`
- `PublicDocsBundleServiceTests`

## 关联文档

- [REQ-20260518-004](../../docs/requirements/REQ-20260518-004-openai-vector-store-local-ingestion-artifact-baseline.md)
- [TASK-20260514-023](../backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 当前状态

- 2026-05-18：已完成实现、验证和文档/catalog 回写，准备归档到 `tasks/done/`。

## 实现结果

- `GatewayAsyncResourceService` 在 attachment 创建时生成本地 ingestion metadata，记录 `local-chunk-v1`、文件 hash、真实 bytes、token 估算、chunk_count 与 chunks。
- `createVectorStore(file_ids)`、single attach、file batch 三条路径均复用同一 attachment ingestion 逻辑。
- `searchVectorStore` 优先使用 ingestion chunks；旧 attachment 缺失 ingestion 时继续回退读取原始 gateway file。
- 无法读取或跨 Distributed Key 的 file 不再生成 completed attachment。
- `provider-catalog.json`、`PublicDocsBundleService`、`docs/public-api-compatibility.md`、`docs/openapi/public-openapi.json` 与对应测试已同步本地 chunk ingestion 能力边界。

## 验证结果

通过命令：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoresTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreFilesTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreFileBatchesTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreSearchTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

## 遗留问题

- 真实 embedding/vector index ingestion、语义向量检索、hosted `file_search_call` lifecycle 和真实 smoke 仍在父任务后续切片内。
- `metadata_json` 承载 chunk 文本不是最终索引存储方案，后续真实向量入库需要拆分存储、清理和迁移策略。
