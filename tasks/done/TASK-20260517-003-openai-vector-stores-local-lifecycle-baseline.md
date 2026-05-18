# TASK-20260517-003 OpenAI Vector Stores 本地 Lifecycle 基线

状态：Done
优先级：High
类型：子任务
父任务：[TASK-20260514-023](../backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)
上游来源：[REQ-20260517-003](../../docs/requirements/REQ-20260517-003-openai-vector-stores-local-lifecycle-baseline.md)

## 背景

`TASK-20260514-023` 要求覆盖 Vector Stores 全栈兼容，但当前项目没有 `/v1/vector_stores` controller。第一步需要补本地 `vector_store` 对象 lifecycle，后续再拆 search、files、file_batches 和 Responses `file_search` tool。

## 目标

- 新增 OpenAI-compatible Vector Store create/list/retrieve/update/delete。
- 使用 `gateway_async_resource` 保存当前 Distributed Key 下的本地 vector store lineage。
- list 支持 `after`、`limit`、`order`。
- update 支持 `name`、`metadata`、`expires_after`。
- delete 软删除并返回 OpenAI-style deleted object。
- 同步 public docs、OpenAPI、provider catalog 和父任务。

## 非目标

- 不实现 vector store search。
- 不实现 vector store files / file batches。
- 不让 Responses `file_search` tool 真执行。
- 不访问真实 OpenAI。

## 输入

- `GatewayAsyncResourceService`
- `GatewayAsyncResourceRepository`
- `OpenAiProtocolPathMatcher`
- `TASK-20260514-023`
- OpenAI Vector Stores API Reference

## 输出

- `OpenAiVectorStoresController`
- Vector store service methods
- Controller/service/docs/provider catalog tests
- 文档与任务回写

## 影响范围

- OpenAI ingress。
- Async resource storage。
- Public compatibility docs 和 OpenAPI。
- Provider catalog conformance。

## 依赖

- `TASK-20260514-030` OpenAI 横切协议基线。
- `TASK-20260514-021` Files lifecycle 后续会影响 vector store files 子资源。

## 风险

- 本地对象生命周期和 OpenAI 托管检索能力混淆。
- 删除/读取跨租户对象。
- `file_ids` 被误认为完成真实 ingestion。

## 验收标准

- create/list/get/update/delete 全部可通过本地测试验证。
- list cursor 与 limit/order 行为稳定。
- 跨 Distributed Key retrieve/delete 被拒绝。
- docs/provider catalog 明确 search/files/file_batches 仍是剩余切片。

## 测试边界

- `OpenAiVectorStoresControllerTests`
- `GatewayAsyncResourceVectorStoresTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`

## 关联文档

- [REQ-20260517-003](../../docs/requirements/REQ-20260517-003-openai-vector-stores-local-lifecycle-baseline.md)
- [TASK-20260514-023](../backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 实现结果

- 新增 `GatewayAsyncResourceType.VECTOR_STORE`。
- `GatewayAsyncResourceService` 增加 `createVectorStore`、`listVectorStores`、`getVectorStore`、`updateVectorStore`、`deleteVectorStore`。
- 新增 `OpenAiVectorStoresController`，公开 `/v1/vector_stores` create/list/retrieve/update/delete。
- Vector Store 使用 `vs_` 本地 id，返回 `object=vector_store`，包含 `created_at`、`last_active_at`、`status`、`usage_bytes`、`file_counts`、`metadata`、`expires_after`、`expires_at`。
- 删除使用软删除并返回 `object=vector_store.deleted`、`deleted=true`。
- Public docs、OpenAPI snapshot、provider catalog、审计报告和父任务已同步 `openai.vector-stores-local-lifecycle`。

## 验证情况

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoresTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiVectorStoresControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

结果：通过。

## 遗留问题

- `search`、`files`、`file_batches`、真实 vector ingestion 和 Responses `file_search` tool resource binding 仍属于 `TASK-20260514-023` 后续切片。
- 本轮没有访问真实 OpenAI Vector Stores API，不把本地 lifecycle 解释为上游托管检索完成。

## 当前状态

- 2026-05-17：已完成并归档。
