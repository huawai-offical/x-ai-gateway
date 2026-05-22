# TASK-20260514-014 OpenAI 对话与 Tools 支撑资源族覆盖差距补齐

状态：Done
优先级：High
类型：子任务
父任务：[REQ-20260514-008](../../docs/requirements/REQ-20260514-008-openai-api-compatibility-deep-audit.md)
上游来源：[REP-20260514 OpenAI API 完整兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)

## 背景

OpenAI 官方 API Reference 覆盖大量资源族，但当前产品边界已收窄为对话、streaming、tools/function calling 和必要的 file_search/RAG 支撑。Fine-tuning、Batches、Evals、Administration、Videos、Skills、Containers 全量 lifecycle 等非核心官方 API 不再作为 backlog 目标。

## 目标

- 建立 OpenAI 对话/tools 支撑资源族覆盖矩阵，逐项标注 `Supported`、`Partial`、`Out of scope`。
- 保留 Chat/Responses/Conversations/Webhooks/tools、Files/Uploads/Models 最小支撑、Vector Stores file_search 本地支撑和 Realtime 基线。
- 将 Fine-tuning、Batches、Evals、Administration、Videos、Skills、Containers 全量 lifecycle 从当前 backlog 删除或标记为范围外。
- Provider catalog 不再把 OpenAI Direct 误标为官方全量 API 替代。

## 非目标

- 不在一个任务内一次性实现所有资源族代码。
- 不把本项目自身 Admin Console 等同于 OpenAI Administration API。
- 不实现已被 [ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md) 排除的非核心官方 API。

## 输入

- 官方 OpenAI API Reference 资源导航。
- `GatewayRequestFeatureService`、`AsyncLifecycleGatewayResourceExecutor`、OpenAI resource controllers、Endpoint conformance matrix。
- 当前 provider catalog 与 public OpenAPI。
- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)。

## 输出

- OpenAI conversation/tools resource family coverage matrix。
- 资源族实现拆分任务或 out-of-scope 决策。
- 已删除非核心官方 API backlog 的记录。
- Provider catalog `unsupportedFeatures` 与文档声明更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/`
- `src/test/resources/conformance/`
- `src/main/resources/provider-catalog.json`
- `docs/public-api-compatibility.md`

## 依赖

- 官方 OpenAI key 与可用组织权限，用于真实 smoke 分类。
- 对对象生命周期数据模型的存储策略决定：本地编排、上游 passthrough、或混合 lineage。

## 风险

- 旧 docs/tasks 可能残留全量 API 语义。
- Vector Stores File Batches 是 file_search 支撑能力，不能和 OpenAI `/v1/batches` 混淆。

## 验收标准

- 每个保留资源族都有明确状态与任务归属。
- 已实现资源族和未实现资源族在 provider catalog、public docs、conformance matrix 中一致。
- Fine-tuning、Batches、Evals、Administration 等非核心 API 不再作为当前 backlog 目标。

## 测试边界

- 静态 coverage matrix test。
- Endpoint conformance matrix 更新。
- 新增资源入口采用 controller/service 单测；真实 smoke 在缺 key 或缺权限时必须输出 skipped reason。

## 关联文档

- [REQ-20260514-008](../../docs/requirements/REQ-20260514-008-openai-api-compatibility-deep-audit.md)
- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)
- [REP-20260514 OpenAI API 完整兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)

## 下游细分任务

- [TASK-20260514-020 OpenAI Audio、Images、Embeddings、Moderations 参数边界](TASK-20260514-020-openai-multimodal-supporting-parameters.md)
- [TASK-20260514-021 OpenAI Files、Uploads、Models 对话支撑最小生命周期](TASK-20260514-021-openai-files-uploads-models-functional-support.md)
- [TASK-20260514-023 OpenAI Vector Stores 对话 RAG 支撑面](../done/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 已删除非核心任务

- `TASK-20260514-022 OpenAI Fine-tuning 全生命周期`
- `TASK-20260514-024 OpenAI Containers 与 Code Interpreter 文件`：已按 ADR-0010 判定为非核心官方 API，不再保留独立 Backlog 任务。
- `TASK-20260514-025 OpenAI Videos API 兼容面`：已按 ADR-0010 判定为非核心官方 API，不再保留独立 Backlog 任务。
- `TASK-20260514-026 OpenAI Evals、Graders 与 Runs API`
- `TASK-20260514-027 OpenAI Skills API 与工具分发`
- `TASK-20260514-028 OpenAI Administration API 权限隔离与只读优先`

删除依据：[REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md) 与 [ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md)。

## 已完成切片

- [TASK-20260516-011 OpenAI Batches List Envelope 与本地游标分页](../done/TASK-20260516-011-openai-batches-list-envelope.md)：OpenAI Direct `GET /v1/batches` 已从 accepted exception 转为本地 lineage list 能力。
- [TASK-20260516-012 OpenAI Models Delete 与 Fine-tuned Model 删除边界](../done/TASK-20260516-012-openai-models-delete-finetuned-boundary.md)：OpenAI Direct `DELETE /v1/models/{model}` 已转为当前 DistributedKey 下 gateway-registered fine-tuned model registry delete。
- [TASK-20260516-013 OpenAI Fine-tuning Events/Checkpoints 本地 Lineage 列表](../done/TASK-20260516-013-openai-fine-tuning-events-checkpoints-local-lineage.md)：OpenAI Direct `GET /v1/fine_tuning/jobs/{jobId}/events` 与 `GET /v1/fine_tuning/jobs/{jobId}/checkpoints` 已转为当前 DistributedKey 下 gateway-tracked tuning job 的本地 lineage list。

以上历史切片保留归档记录，但不再代表当前产品范围继续推进。

## 本轮完成结果

- 2026-05-18 根据 [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md) 和 [ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md)，已将任务范围从 OpenAI 官方全量资源族收窄为对话、tools、RAG/file_search 和必要支撑资源。
- 已删除或停止推进 Fine-tuning、OpenAI `/v1/batches`、Evals、Administration、Videos、Skills、Containers 全量 lifecycle 等非核心官方 API backlog。
- 已保留 Vector Store File Batches 作为 Responses `file_search` 的本地 ingestion 支撑，并明确其不等同于 OpenAI `/v1/batches`。

## 验证结果

- `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests"`：通过。
