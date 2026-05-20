# REQ-20260518-005 对话与 Tools 功能性服务 API 范围收窄

状态：Done
日期：2026-05-18
上游来源：用户明确要求 Fine-tuning / Batches / Evals / Admin 等非核心能力 out；Anthropic、Gemini、Vertex、Codex API 也需要按 OpenAI API 标准功能区收紧，只做对话、tools 等功能性服务 API。

## 背景

此前 `REQ-20260514-009` 和 `TASK-20260514-016` 以 OpenAI API 全量覆盖为目标，拆出了 Fine-tuning、Batches、Evals、Administration、Videos、Skills、Containers 等资源族任务。随着产品核心理念明确为多 provider 的对话、streaming、tools/function calling、多模态能力与必要的对话支撑服务，继续追求 OpenAI、Anthropic、Gemini、Vertex、Codex 或其它主流 provider 的全量官方 API 会扩大实现面、测试面、权限风险和真实 smoke 成本。

本轮需要把任务体系和代码事实源同步收窄：保留对话、tools 以及直接支撑对话/RAG/tools 的最小资源面；删除或停止推进 Fine-tuning、Batches、Evals、Administration 等非核心官方 API 兼容面。已有本地 RAG/file_search 基线可作为 tools 支撑能力保留，但不再升级为官方全量 Vector Stores parity。

## 目标

- 将产品范围固定为对话、streaming、tools/function calling、多模态输入输出、model discovery、必要的文件/RAG 支撑、认证、错误、限流、审计和真实 smoke。
- 从当前 backlog 删除 Fine-tuning、Batches、Evals、Administration 等非核心 API 任务；混合任务需要收窄为对话/tools 支撑最小面。
- 扫描代码中已经暴露的非核心 API 入口，能安全删除的公开 controller、docs、catalog、conformance 和测试一并移除。
- Anthropic、Gemini 等 provider 同步执行同一产品边界：不再为 message batches、batch prediction、provider admin、fine-tuning、evals 等官方非核心 API 建立兼容目标。
- Anthropic、Gemini、Vertex、Codex 必须按 OpenAI 标准功能区收紧：保留 Messages/GenerateContent/Responses、tools、embeddings/files 等可映射支撑面；拒绝 provider batch jobs、tuning、evals、pipeline/job/admin，以及非 Responses 的 Codex 内部 API。
- 保留内部 Admin Console、运营、计费、凭证和观测 API；这些是平台自身管理面，不等同于 OpenAI/Anthropic 官方 Admin API。

## 非目标

- 不删除 Chat、Responses、Messages、GenerateContent、streaming、tools/function calling、webhooks、realtime 基线等核心能力。
- 不删除已经服务于 Responses `file_search` 的本地 Vector Store / file attachment / local search 基线。
- 不删除平台自身 `/admin/*` 管理后台能力。
- 不做数据库历史数据清理或迁移。
- 不在本轮重新设计 provider catalog 或 public docs 的生成体系。

## 范围

- `tasks/backlog/` 中 OpenAI 全量覆盖相关任务。
- `tasks/index.md` 和 `docs/index.md`。
- OpenAI/Anthropic/Gemini 非核心 API controller、mapper、encoder、conformance fixture、public OpenAPI、provider catalog 和文档事实源。
- Vertex provider catalog、native compatibility matrix、Codex official account Responses smoke 边界说明。
- `GatewayRequestFeatureService`、`GatewayRequestSemantics`、canonical resource mapping/rendering 中的非核心 API 路由。

## 风险

- 代码中 Batch/Fine-tuning 与 async resource、fine-tuned model registry 有历史耦合，直接删除可能影响 Models delete 或已完成测试。
- `batch` 一词也用于 Vector Store File Batches；该能力支撑 file_search ingestion，不能按 OpenAI `/v1/batches` 一起删除。
- `admin` 包是本项目管理后台，不属于外部 provider Admin API，不能误删。
- public OpenAPI、docs bundle、conformance matrix 和测试断言必须同步，否则会出现文档仍宣称支持已删除 API 的漂移。

## 验收标准

- 当前 backlog 不再保留 Fine-tuning、Batches、Evals、Administration 等非核心官方 API 兼容任务。
- OpenAI 全量覆盖父任务被收窄为对话/tools 功能性 API 范围，不再作为全量 API parity 目标。
- 公开代码入口不再暴露 `/v1/batches`、`/v1/fine_tuning/jobs*`、Anthropic `/v1/messages/batches*`、Gemini/Vertex batch prediction 等非核心 API。
- Provider catalog 与 native compatibility matrix 明确 Anthropic、Gemini、Vertex、Codex 只属于 OpenAI 标准功能区，不承诺 provider 全量官方 API。
- Provider catalog、public docs、OpenAPI snapshot 和 conformance matrix 不再把这些非核心 API 标为支持面。
- 相关测试通过；无法全量验证时需记录具体失败和原因。

## 测试边界

- Docs bundle / OpenAPI snapshot / provider catalog tests。
- Protocol path matcher 和 feature semantics tests。
- 相关 controller tests 删除后，核心 Chat/Responses/Vector Store file_search tests 不应回退。
- 不做真实 provider smoke；本轮为范围清理和静态/单元验证。

## 关联文档

- [REQ-20260514-009 OpenAI API 全量覆盖任务体系](REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI API 全量覆盖任务拆解](../reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
- [ADR-0010 对话与 Tools 功能性服务 API 作为产品范围](../decisions/ADR-0010-functional-service-api-scope.md)

## 实现结果

- Backlog 已从 OpenAI 全量覆盖重规划为对话、tools、RAG/file_search 支撑、横切协议和真实 smoke。
- 已删除 OpenAI `/v1/batches`、`/v1/fine_tuning/jobs*`、Anthropic message batches、Gemini batch prediction、public tunings 等公开入口和对应 tests。
- Provider catalog、public docs、OpenAPI、native compatibility matrix 已补充跨 Anthropic/Gemini/Vertex/Codex 的 OpenAI 标准功能区约束。

## 验证结果

- 定向测试通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.NativeCompatibilityServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderReferenceGapServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayPublicResourceServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GoogleNativeNamespaceControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatRoutePolicyServiceTests"
```

结果：通过，91 tests completed。

## 遗留问题

- 原先保留的 `GatewayAsyncResourceType.BATCH/TUNING` 与历史存储/观测兼容层，已由 [REQ-20260518-006](REQ-20260518-006-non-core-api-code-eradication.md) 按“不兼容历史数据”的口径彻底清理。
- 后续继续按 [REP-20260518](../reports/REP-20260518-functional-service-api-backlog-replan.md) 的 P0/P1/P2 顺序推进，只覆盖对话、tools 和直接支撑能力。
