# TASK-20260516-011 OpenAI Batches List Envelope 与本地游标分页

状态：Done
优先级：High
类型：子任务
父任务：[TASK-20260514-021](TASK-20260514-021-openai-files-uploads-models-functional-support.md)
上游来源：[TASK-20260514-014](../backlog/TASK-20260514-014-openai-resource-family-coverage-gap.md)、[TASK-20260514-015](../backlog/TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md)、[REP-20260514 OpenAI API 兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)

## 背景

官方 Batch API 暴露 `GET /v1/batches`，使用 `limit` 与 `after` 分页列出组织内 Batch 对象。当前仓库只实现了 `POST /v1/batches`、`GET /v1/batches/{batchId}` 和 `POST /v1/batches/{batchId}/cancel`，`accepted-exceptions.json` 仍把 `GET /v1/batches` 标记为未公开缺口，导致执行面、conformance 与公开能力说明不一致。

## 目标

- 在 OpenAI ingress 暴露 `GET /v1/batches?limit=&after=`。
- 将 `GET /v1/batches` 纳入 `GatewayRequestFeatureService`、`TranslationOperation`、`GatewayRequestSemantics` 与 `AsyncLifecycleGatewayResourceExecutor`。
- 在 `GatewayAsyncResourceService` 基于 `gateway_async_resource` 的 BATCH lineage 返回 OpenAI list envelope。
- 支持官方 `limit` 默认 20、范围 1 到 100，支持 `after` cursor，且只返回当前 DistributedKey 下未删除 BATCH 对象。
- 回收 `accepted-exceptions.json` 中 `/v1/batches [GET]` 缺口，并更新 provider catalog/public compatibility 的边界说明。

## 非目标

- 不在本任务内补齐 Files/Uploads/Models 的其他生命周期缺口。
- 不在本任务内实现 Fine-tuning events/checkpoints/pause/resume。
- 不在 list 阶段主动 fan-out 同步所有远端 batch 状态；状态同步仍由 `GET /v1/batches/{batchId}` 与已有 lineage 同步负责。

## 输入

- 官方 OpenAI Batch API Reference：`GET /v1/batches`，query 参数 `after`、`limit`。
- `OpenAiBatchesController`
- `GatewayAsyncResourceService`
- `GatewayRequestFeatureService` / `TranslationOperation` / `GatewayRequestSemantics`
- `AsyncLifecycleGatewayResourceExecutor`
- `src/test/resources/conformance/accepted-exceptions.json`

## 输出

- `GET /v1/batches` 可调用并返回：
  - `object=list`
  - `data=[batch...]`
  - `first_id`
  - `last_id`
  - `has_more`
- Controller、service、interop/conformance 回归测试。
- 对应文档和任务状态回写。

## 影响范围

- OpenAI Batch ingress。
- Async resource repository 查询。
- Non-chat interop semantics 与 conformance matrix。
- Provider catalog 与 public compatibility 文档说明。

## 依赖

- `TASK-20260515-010` 已提供 list pagination envelope 基线。
- `TASK-20260516-010` 已把 OpenAI Provider Catalog 未完成边界显式化。

## 风险

- `after` 若使用不存在或跨 DistributedKey 的 id，不能泄漏其他 key 的对象，应返回空页或从默认起点继续的明确策略。本任务采用官方 cursor 语义：只在当前 key 与 BATCH 类型内定位 cursor；找不到 cursor 时返回空页，避免跨租户推断。
- 历史 BATCH response payload 可能缺少 `object` 或 `id`，需要 list 输出保持已持久化 payload，不额外伪造远端字段。
- 本地 list 不代表上游组织全量 list，只代表经本 gateway 创建并落库的 lineage 对象；该边界必须写入文档。

## 验收标准

- `GET /v1/batches?limit=1` 通过 controller 测试，并把 query 参数传入生命周期执行链路。
- `GatewayAsyncResourceService.listBatches` 能按 `created_at desc,id desc` 分页，返回 `has_more/first_id/last_id`，并隔离 DistributedKey。
- `GatewayRequestFeatureService.describe("GET", "/v1/batches", null)` 返回 `batch_list` 与 `LOCAL_CATALOG`。
- `accepted-exceptions.json` 不再包含 `/v1/batches [GET]`。
- targeted tests 通过。

## 测试边界

- `OpenAiBatchesControllerTests`
- `GatewayAsyncResourceServiceTests`
- `GatewayRequestFeatureServiceTests`
- `EndpointConformanceMatrixTests`
- `ProviderCatalogLoaderTests`
- `PublicDocsBundleServiceTests`

## 关联文档

- [TASK-20260514-021](TASK-20260514-021-openai-files-uploads-models-functional-support.md)
- [REP-20260514 OpenAI API 兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 当前状态

- 2026-05-16：已闭环。`GET /v1/batches` 已接入 OpenAI ingress、interop semantics、async lifecycle executor 与 `GatewayAsyncResourceService.listBatches`；返回当前 DistributedKey 下经 gateway 创建并落库的 Batch lineage list envelope。

## 实现结果

- `OpenAiBatchesController` 新增 `GET /v1/batches`，接收 `after` 与 `limit` query，并把 query payload 传入生命周期执行链路。
- `TranslationOperation` 新增 `BATCH_LIST`，`GatewayRequestFeatureService`、`GatewayRequestSemantics`、`CanonicalExecutionPlan`、`DefaultCanonicalResourceMapper` 与 `AsyncLifecycleGatewayResourceExecutor` 已同步识别 `batch_list` / `LOCAL_CATALOG`。
- `GatewayAsyncResourceService.listBatches` 使用 `gateway_async_resource` 中当前 DistributedKey 的 BATCH lineage，以 `created_at desc,id desc` 返回 `object=list`、`data`、`has_more`、`first_id`、`last_id`。
- list 输出排除 Anthropic native `message_batch` lineage，避免 generic `/v1/batches` 混入 `/v1/messages/batches` 语义。
- `accepted-exceptions.json` 已移除 `/v1/batches [GET]`；`endpoint-conformance-matrix.json` 新增 `batch_list` 行。
- `provider-catalog.json` 新增 `openai.batches-list-local-catalog` conformance check，并移除 `batches_list` unsupported feature。
- `docs/public-api-compatibility.md`、`PublicDocsBundleService` 与 `docs/openapi/public-openapi.json` 已声明 `GET /v1/batches` 是 gateway-tracked local lineage list，不承诺枚举上游组织全量历史。

## 验证记录

通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiBatchesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"
```

## 遗留与后续

- Batches error file lineage、endpoint allowlist 仍归属 `TASK-20260514-021` 后续切片。
- Models delete、Fine-tuning events/checkpoints/pause/resume 仍保持在对应 backlog 中，不由本任务展开。
