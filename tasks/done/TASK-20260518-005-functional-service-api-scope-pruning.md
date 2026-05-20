# TASK-20260518-005 对话与 Tools 功能性服务 API 范围清理

状态：Done
优先级：Critical
类型：父任务
上游来源：[REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)

## 背景

用户明确要求 Fine-tuning / Batches / Evals / Admin 等非核心能力 out；Anthropic、Gemini、Vertex、Codex API 也按 OpenAI API 标准功能区收紧，只做对话、tools 等功能性服务 API。当前 backlog 和部分代码仍保留 OpenAI 全量 API 覆盖时代的非核心资源族任务与公开入口，需要清理。

## 目标

- 盘点 `tasks/backlog/` 中非核心官方 API 任务。
- 删除 Fine-tuning、Batches、Evals、Administration 等不再推进的任务文件和索引入口。
- 将混合任务收窄为对话/tools 支撑面，避免继续追全量 API。
- 将 Anthropic、Gemini、Vertex、Codex 的 provider/native API 同步收紧到 OpenAI 标准功能区。
- 扫描并删除代码中可安全移除的非核心 API 公开入口、docs、catalog、conformance 和测试断言。
- 回写需求、ADR、任务索引和验证结果。

## 非目标

- 不删除平台自身 `/admin/*` 管理后台。
- 不删除 Chat/Responses/Messages/GenerateContent/tools/function calling。
- 不删除支撑 Responses `file_search` 的本地 Vector Store File Batch 能力。
- 不做数据库历史数据清理。

## 输入

- `REQ-20260518-005`
- `TASK-20260514-016` 至 `TASK-20260514-031`
- OpenAI/Anthropic/Gemini/Vertex/Codex 非核心 API controller、mapper、renderer、docs、catalog 与 tests

## 输出

- 清理后的 backlog 和任务索引。
- 删除或收窄后的非核心 API 代码入口。
- 更新后的 provider catalog、public docs、OpenAPI snapshot、conformance fixture。
- 验证命令和结果。

## 影响范围

- `tasks/backlog/`
- `tasks/index.md`
- `docs/requirements/`
- `docs/decisions/`
- `docs/index.md`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/anthropic/`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/google/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/canonical/`
- `src/test/resources/conformance/`
- `docs/openapi/public-openapi.json`

## 依赖

- 当前代码中非核心 API 与核心路径的耦合情况。
- 现有 docs/catalog/OpenAPI 测试断言。

## 风险

- 误删 Vector Store File Batches 会破坏本地 file_search/RAG 支撑。
- 误删平台自身 Admin Console 会破坏管理后台。
- 仅删任务不删代码会继续暴露不符合产品定位的 API。
- 仅删代码不改 docs/catalog 会造成公开事实源漂移。

## 验收标准

- 非核心 API backlog 任务已删除或收窄。
- OpenAI `/v1/batches` 与 `/v1/fine_tuning/jobs*`、Anthropic `/v1/messages/batches*`、Gemini `/v1beta/batches*` 不再作为公开支持入口。
- Provider catalog 与 native compatibility matrix 明确 Anthropic、Gemini、Vertex、Codex 只保留 OpenAI 标准功能区，不承诺官方全量 API。
- Fine-tuning、Batches、Evals、Administration 不再出现在 provider catalog 支持标签或 public docs 支持面里。
- 核心对话/tools/RAG 测试仍通过。

## 测试边界

- `ProviderCatalogLoaderTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `OpenAiProtocolPathMatcherTests`
- `GatewayRequestFeatureServiceTests`
- 必要的 OpenAI/Anthropic/Gemini controller tests。

## 关联文档

- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)
- [ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md)

## 当前状态

- 2026-05-18：已完成范围清理与重规划。

## 实现结果

- 删除 OpenAI `/v1/batches`、`/v1/fine_tuning/jobs*`、`DELETE /v1/models/{model}` fine-tuned owner-role delete、Anthropic Message Batches、Gemini batch prediction、public `/api/v1/tunings` 的公开 controller、mapper、encoder、docs、catalog、conformance 和相关 tests。
- Provider catalog、public docs、OpenAPI、native compatibility matrix、ProviderReferenceGap 已统一为 OpenAI 标准功能区口径。
- Site capability truth、execution support matrix、upstream site policy 与 public resource operations 已将 Batch/Tuning/Anthropic Message Batch 判定为 unsupported，避免内部事实源继续暗示可用。
- Backlog 已删除 6 个非核心任务，`TASK-20260514-014` 和 `TASK-20260514-015` 已归档到 done，剩余 11 个任务按 Critical/High/Medium 重新排序。

## 验证结果

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.NativeCompatibilityServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderReferenceGapServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayPublicResourceServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GoogleNativeNamespaceControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatRoutePolicyServiceTests"
```

结果：通过，91 tests completed。

## 遗留问题

- 原先保留的 `GatewayAsyncResourceType.BATCH/TUNING` 与历史存储/观测兼容层，已由 [TASK-20260518-006](TASK-20260518-006-non-core-api-code-eradication.md) 按“不兼容历史数据”的口径彻底清理。
- 后续 P0 仍需继续推进 Chat、Responses、横切协议和真实 smoke 的功能性服务 API 闭环。
