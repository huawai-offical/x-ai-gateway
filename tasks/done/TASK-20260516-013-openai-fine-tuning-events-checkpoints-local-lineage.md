# TASK-20260516-013 OpenAI Fine-tuning Events/Checkpoints 本地 Lineage 列表

状态：Done  
优先级：High  
类型：子任务切片  
父任务：[TASK-20260514-022](../backlog/TASK-20260514-022-openai-fine-tuning-full-lifecycle.md)  
上游来源：[TASK-20260514-014](../backlog/TASK-20260514-014-openai-resource-family-coverage-gap.md)、[TASK-20260514-015](../backlog/TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md)、[REQ-20260516-013](../../docs/requirements/REQ-20260516-013-openai-fine-tuning-events-checkpoints-local-lineage.md)

## 背景

OpenAI Fine-tuning 官方 API 已公开 job events 与 checkpoints list endpoint。当前 gateway 只公开 create/list/get/cancel，`accepted-exceptions.json` 仍把 `/v1/fine_tuning/jobs/{jobId}/events` 和 `/v1/fine_tuning/jobs/{jobId}/checkpoints` 标为未公开缺口。

本项目已经在 `gateway_async_resource` 里持久化 tuning job 的 request/response/metadata，并在 metadata 中记录本地 lifecycle events 与 fine-tuned model 注册信息。因此本切片先补一个可验证、低成本、租户隔离明确的本地 lineage list，而不是一次性实现完整 OpenAI 上游历史 passthrough。

## 目标

- 新增 `GET /v1/fine_tuning/jobs/{jobId}/events`。
- 新增 `GET /v1/fine_tuning/jobs/{jobId}/checkpoints`。
- 支持 `after`、`limit` 查询参数，默认 `limit=20`，范围 `1..100`。
- events 从当前 job 的 metadata events 派生为 OpenAI-style `fine_tuning.job.event`。
- checkpoints 仅从当前 job 的 response/metadata 中已有 checkpoint 或 registered fine-tuned model 派生。
- 将两个 endpoint 纳入 interop semantics、async lifecycle executor、conformance matrix、provider catalog、public docs 和 OpenAPI。

## 非目标

- 不实现上游 OpenAI passthrough events/checkpoints。
- 不实现 pause/resume、checkpoint permissions、grader/integrations。
- 不发起真实 Fine-tuning 训练或高成本 smoke。
- 不扩展 OpenAI-compatible 第三方站点为通用 object lifecycle 能力。

## 输入

- 官方 OpenAI Fine-tuning events/checkpoints API Reference。
- `OpenAiFineTuningJobsController`
- `GatewayAsyncResourceService`
- `GatewayRequestFeatureService`
- `TranslationOperation`
- `GatewayRequestSemantics`
- `AsyncLifecycleGatewayResourceExecutor`
- `src/test/resources/conformance/accepted-exceptions.json`

## 输出

- OpenAI-compatible events/checkpoints endpoints。
- 本地 lineage list 构造逻辑。
- Controller、service、interop、conformance 和 public docs tests。
- 文档与任务状态回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/canonical/`
- `src/main/resources/provider-catalog.json`
- `src/test/resources/conformance/`
- `docs/openapi/public-openapi.json`
- `docs/public-api-compatibility.md`

## 依赖

- `TASK-20260515-010` 已建立 list envelope/cursor 基线。
- `TASK-20260516-012` 已建立 fine-tuned model registry 边界。
- `gateway_async_resource` 必须能按当前 DistributedKey 与 jobId 查询到 TUNING 对象。

## 风险

- 本地 lineage 不等于 OpenAI 上游完整事件历史；必须显式写入公开文档。
- checkpoint 派生不能凭空制造训练中间态；无本地证据时必须返回空列表。
- 跨 DistributedKey jobId 必须返回未找到，不允许泄露事件或 checkpoint。
- `after` cursor 如果不存在，返回空页以避免跨租户推断。

## 验收标准

- `GET /v1/fine_tuning/jobs/{jobId}/events?limit=&after=` 返回 list envelope，并只包含当前 job 的本地 events。
- `GET /v1/fine_tuning/jobs/{jobId}/checkpoints?limit=&after=` 返回 list envelope；完成且存在 fine-tuned model 证据时返回 final checkpoint，否则返回空列表。
- `GatewayRequestFeatureService.describe` 能识别两个 endpoint，并设置 `STORED_LINEAGE`。
- `accepted-exceptions.json` 移除 `openai-unexposed-list-events-checkpoints`。
- provider catalog、public docs、public OpenAPI 和审计报告描述一致。

## 测试边界

- `OpenAiFineTuningJobsControllerTests`
- `GatewayAsyncResourceServiceTests`
- `GatewayRequestFeatureServiceTests`
- `EndpointConformanceMatrixTests`
- `ProviderCatalogLoaderTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- 不运行真实 OpenAI smoke。

## 关联文档

- [REQ-20260516-013](../../docs/requirements/REQ-20260516-013-openai-fine-tuning-events-checkpoints-local-lineage.md)
- [TASK-20260514-022](../backlog/TASK-20260514-022-openai-fine-tuning-full-lifecycle.md)
- [REP-20260514 OpenAI API 兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)
- OpenAI Fine-tuning events API Reference：`https://developers.openai.com/api/reference/resources/fine_tuning/subresources/jobs/methods/list_events`
- OpenAI Fine-tuning checkpoints API Reference：`https://developers.openai.com/api/reference/resources/fine_tuning/subresources/jobs/subresources/checkpoints/methods/list`

## 当前状态

- 2026-05-16：已闭环。Fine-tuning events/checkpoints 已接入 OpenAI ingress、interop semantics、async lifecycle executor 与 `GatewayAsyncResourceService` 本地 lineage list；对应 accepted exception 已回收。

## 实现结果

- `OpenAiFineTuningJobsController` 新增 `GET /v1/fine_tuning/jobs/{jobId}/events` 与 `GET /v1/fine_tuning/jobs/{jobId}/checkpoints`，接收 `after`、`limit` query 参数并传入生命周期执行链路。
- `TranslationOperation`、`GatewayRequestFeatureService`、`GatewayRequestSemantics`、`CanonicalExecutionPlan`、`DefaultCanonicalResourceMapper` 与 `AsyncLifecycleGatewayResourceExecutor` 已识别 `tuning_events_list` / `tuning_checkpoints_list`，并统一走 `STORED_LINEAGE`。
- `GatewayAsyncResourceService` 新增本地 list 构造：
  - events 从 `metadata_json.events` 派生为 OpenAI-style `fine_tuning.job.event` item。
  - checkpoints 优先读取 metadata `checkpoints`；没有显式 checkpoint 时，仅在 response/metadata 存在完成模型证据时派生 final checkpoint；无证据返回空列表。
  - `after` cursor 不存在时返回空页，避免跨租户推断；`limit` 复用 1 到 100 的 list 基线。
- `accepted-exceptions.json` 已移除 `openai-unexposed-list-events-checkpoints`；`endpoint-conformance-matrix.json` 增加两个 OpenAI Direct endpoint。
- `provider-catalog.json` 新增 `openai.fine-tuning-events-checkpoints-local-lineage` conformance check，并把未完成边界收敛为 pause/resume、checkpoint permissions 和 graders。
- `PublicDocsBundleService` 与 `docs/openapi/public-openapi.json` 已公开两个 endpoint，并写明当前为 gateway local lineage，不声明同步上游完整事件历史。
- 上游 backlog 与审计报告已回写：`TASK-20260514-014`、`015`、`022` 和 OpenAI API audit/breakdown 报告均把 events/checkpoints 从缺口移到已完成切片。

## 验证记录

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiFineTuningJobsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceMapperTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"
```

结果：通过。

## 遗留边界

- 不调用真实 OpenAI 上游 events/checkpoints passthrough；当前只返回本 gateway 已记录的本地 lineage。
- pause/resume、checkpoint permissions、grader/integrations 仍归属 `TASK-20260514-022` 后续切片。
- OpenAI-compatible 第三方站点仍不扩展为通用 object lifecycle 替代能力。
