# TASK-20260524-001-06 Provider-specific OpenAI-compatible Runtime Profile 拆分

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260524-001](../in-progress/TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)  
上游来源：[REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md) / [REP-20260524-001](../../docs/reports/REP-20260524-001-native-adapter-minimum-contract.md)

## 背景

MiMo、DeepSeek、xAI、Qwen、Moonshot、Volcengine、MiniMax、Mistral、Perplexity 等已在 provider catalog 中声明 `provider_specific_openai_compatible` contract，但当前运行时 `ProviderType` 仍把这些厂商合并为 `OPENAI_COMPATIBLE`。这会让后续能力矩阵、smoke、错误映射和资源支持判断继续滑回 generic OpenAI-compatible。

## 目标

- 引入 provider-specific runtime profile 或 adapter descriptor，用于区分 MiMo、DeepSeek、xAI 等具名厂商。
- 让 route selection、execution support matrix、site policy、smoke harness 和 observability 能读取具名 profile，而不是仅依赖 `OPENAI_COMPATIBLE`。
- 保留 OpenAI-compatible 协议复用，但不保留 generic fallback 作为默认核心厂商身份。

## 非目标

- 不一次性实现所有厂商私有非 OpenAI-style API。
- 不改动用户已有凭证数据模型的兼容迁移策略，迁移方案需单独设计。
- 不把 Dify/OpenRouter/Together/Fireworks/SiliconFlow 重新加入默认核心支持范围。

## 输入

- `src/main/resources/provider-catalog.json`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/shared/ProviderType.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/shared/UpstreamSiteKind.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/site/UpstreamSitePolicyService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/SiteCapabilityTruthService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/ExecutionSupportMatrixService.java`

## 输出

- provider-specific runtime profile 设计与实现。
- 默认核心具名 OpenAI-compatible 厂商不再通过 generic provider identity 暴露。
- 旧 generic alias 仅作为显式兼容路径或迁移路径存在，不作为默认能力事实源。

## 影响范围

- 路由候选、站点策略、能力快照、smoke 分类、观测日志、文档与 OpenAPI provider 表达。

## 依赖

- `TASK-20260524-001-02` native adapter 最小契约。
- `TASK-20260524-001-05` 文档、OpenAPI 与 smoke 范围对齐。

## 风险

- 直接扩展 `ProviderType` 可能影响数据库枚举、历史凭证和 account group 绑定。
- 若只在 UI 层改名而 runtime 仍合并，会继续误导下游客户端。

## 验收标准

- MiMo、DeepSeek、xAI 至少三个具名厂商在 runtime plan / smoke fixture / observability 中可区分。
- generic `OPENAI_COMPATIBLE` 不再作为默认核心 provider 的公开身份。
- Dify/OpenRouter/Together/Fireworks/SiliconFlow 不因兼容 alias 被重新纳入默认核心支持。

## 测试边界

- route selection 单元测试。
- site policy / capability truth 单元测试。
- functional provider smoke dry-run fixture 测试。
- 迁移兼容测试或明确的迁移记录。

## 关联文档

- [REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)
- [REP-20260524-001](../../docs/reports/REP-20260524-001-native-adapter-minimum-contract.md)
- [MIG-20260524-001](../../docs/migrations/MIG-20260524-001-provider-runtime-profile-non-persistent.md)

## 关联任务

- 父任务：[TASK-20260524-001](../in-progress/TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)
- 前置任务：[TASK-20260524-001-02](TASK-20260524-001-02-native-adapter-minimum-contract.md)
- 相关任务：[TASK-20260524-001-05](../done/TASK-20260524-001-05-docs-openapi-smoke-alignment.md)

## 当前状态

- 2026-05-24：由 native adapter 现状审计拆分进入 backlog。当前仅 catalog 具备 provider-specific contract，runtime 仍需进一步拆分。
- 2026-05-24：进入实施。首批不扩展数据库 `ProviderType` 枚举，先引入运行时 provider-specific profile/descriptor，用 `UpstreamSiteKind`、baseUrl 与 protocol 识别 MiMo、DeepSeek、xAI 等具名厂商，并让 smoke、能力事实源和观测输出优先使用该 runtime profile，避免继续把默认核心厂商暴露成 generic `OPENAI_COMPATIBLE`。
- 2026-05-24：首批 runtime profile 已落地。新增 `ProviderRuntimeProfile`，`CatalogCandidateView` 可从 `siteKind`、`vendorCode` 与 baseUrl 推断 `XIAOMI_MIMO`、`DEEPSEEK`、`XAI` 等运行时身份；MiMo preset 与 conformance fixture 已使用 `XIAOMI_MIMO` site kind；route affinity、`allowedProviderTypes`、interop plan debug、route decision candidate summary 与 Micrometer tags 已补 runtime provider 维度。
- 2026-05-24：本轮保持数据库 `ProviderType` 兼容，账号绑定、成本模型和历史治理仍可继续使用粗粒度 `OPENAI_COMPATIBLE`；runtime provider key 作为可观测、路由亲和和白名单补充维度。Files/Uploads 等对象 lifecycle 未因 MiMo 具名化而自动进入 generic file lifecycle 白名单，后续仍由 capability snapshot、Lossless Translation Matrix 与 `001-08` 继续收口。
- 2026-05-24：补齐 DeepSeek/xAI 的 provider-specific smoke/fixture、interop debug 与 observability 证据，并新增非持久化 migration 记录。本任务完成并准备归档；Cohere/Jina embed/rerank executor 仍由 `001-07` 独立承接。

## 本轮实现结果

- 新增 `src/main/java/com/prodigalgal/xaigateway/gateway/core/shared/ProviderRuntimeProfile.java`。
- `CatalogCandidateView` 增加 `runtimeProfile()` / `runtimeProviderKey()`，并从 MiMo、DeepSeek、xAI 典型 baseUrl 推断具名 `UpstreamSiteKind`。
- `GatewayRouteSelectionService` 的 prefix/fingerprint/model affinity 改用 runtime provider key，并允许 Distributed Key `allowedProviderTypes` 同时匹配粗粒度 `ProviderType` 与具名 runtime provider key。
- `SiteCapabilityTruthService`、`ExecutionSupportMatrixService`、`TranslationExecutionPlanCompiler` 补充 `XIAOMI_MIMO` 分支，避免新增 site kind 落回 generic 或被 switch 漏掉。
- `GatewayObservabilityService` candidate summary、`GatewayInteropPlanService` debug 与 `GatewayRequestLifecycleService` metric tags 已输出 runtime provider 维度。
- `src/test/resources/conformance/site-conformance-fixtures.json` 中 MiMo conformance fixture 已从 `OPENAI_COMPATIBLE_GENERIC` 调整为 `XIAOMI_MIMO`。
- `FunctionalProviderSmokeHttpClient`、`FunctionalProviderSmokeCertificationService` 与 `FunctionalProviderSmokeRecordReplayFixtureVerifier` 已支持 `DEEPSEEK` / `XAI` provider-specific OpenAI-compatible smoke 证据，不再把它们写成 generic `OPENAI_COMPATIBLE`。
- `docs/migrations/MIG-20260524-001-provider-runtime-profile-non-persistent.md` 固化本阶段不扩展数据库枚举的兼容策略。

## 本轮验证

- `.\gradlew.bat compileJava`
- `.\gradlew.bat compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.shared.ProviderRuntimeProfileTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.docs.FunctionalCoverageProviderBoundaryTests" --tests "com.prodigalgal.xaigateway.docs.OpenAiChatParameterEvidenceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayInteropPlanServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayInteropPlanServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.gateway.core.shared.ProviderRuntimeProfileTests"`

## 剩余边界

- Cohere/Jina embed/rerank executor 与 smoke 仍由 `001-07` 承接，不属于本任务 Done 边界。
- 旧 degraded/emulated 能力层与 hard-fail 语义隔离已由 `001-08` 完成。
- 若未来要把 runtime provider key 持久化为数据库枚举或独立表，需要单独迁移设计。
