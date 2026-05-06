# REQ-20260506-008 Provider 生态广度与 Conformance 闭环设计

状态：Done  
关联任务：[TASK-20260506-009 Provider 生态广度与 Conformance 完善](../../tasks/done/TASK-20260506-009-provider-ecosystem-conformance.md)  
来源：[REP-20260506 三个参考项目功能完成度复核](../reports/REP-20260506-reference-feature-completeness-review.md)  
日期：2026-05-06

## 背景

`x-ai-gateway` 已经具备 Provider Catalog、Provider Catalog Marketplace、Provider Site Preset、Capability Matrix、Conformance Matrix 等骨架，但内置 catalog 仍偏窄，只有 OpenAI、Azure OpenAI、DeepSeek、OpenRouter、Anthropic、Gemini 等少数 preset。对照 `new-api-main`、`sub2api-main`、`cc-switch-main` 后，剩余高优任务中最应该先闭环的是 Provider 生态广度与 conformance，因为它会成为路由、计价、公开文档、CLI 接入和后续 provider adapter 扩展的事实基础。

## 目标

- 将内置 provider preset 从小样本扩展为可运营的多 provider 矩阵。
- 让 catalog metadata 不只记录 endpoint，还能说明接入 surface、支持策略、模型族、计价元数据和不支持能力。
- 明确 Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Cohere、Jina Rerank、Dify/OpenAI-compatible generic 的支持策略。
- 让管理端 preset、公开 docs bundle、loader 测试和 conformance 测试都能看到新增 provider 与降级原因。

## 范围

- 扩展 `provider-catalog.json` 内置 preset。
- 扩展 `ProviderPresetDefinition`、`ProviderCatalogLoader`、`ProviderSitePresetResponse` 的 metadata 字段。
- 在 `UpstreamSiteKind` 与 `UpstreamSitePolicyService` 中补齐高价值 provider 的站点识别与策略。
- 在 `SiteCapabilityTruthService`、`ExecutionSupportMatrixService` 中补齐新增 site kind 的能力判定和 accepted degradation 语义。
- 更新 `PublicDocsBundleService`，让公开 docs bundle 展示 catalog provider 支持状态。
- 补 loader、registry、public docs、conformance 相关测试。

## 非目标

- 不提交任何真实 provider key。
- 不一次性实现所有 provider 的原生 adapter。
- 不在本轮保证所有新增 provider 的真实线上 smoke 成功；真实凭证 smoke 后续按 provider 拆分。
- 不把 Rerank、Dify、Midjourney、Suno 等能力误标为全量 native 支持；只记录当前 gateway 可路由或可降级策略。

## 详细设计

### Catalog Metadata

每个 preset 增加以下 metadata：

- `compatibilitySurface`：例如 `openai-compatible-chat`、`native-rerank`、`dify-compatible`。
- `supportStrategy`：例如 `cloud-openai-compatible`、`native-adapter-required`、`gateway-degraded`、`not-supported-yet`。
- `modelFamilies`：运营侧可展示的模型族或能力族。
- `pricingMetadata`：计价事实源或需要运营配置覆盖的说明，不写死真实价格。
- `unsupportedFeatures`：明确不能走当前 gateway surface 的能力和原因。

### Provider 站点策略

- Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Dify、OpenAI-compatible generic：默认走 OpenAI-compatible surface，支持 chat/tools/responses emulation，非 chat 资源只在明确兼容时开放。
- Cohere：保留为 OpenAI-compatible/rerank strategy，当前重点是 Rerank 能力说明。
- Jina：作为 Rerank/Embeddings provider，Rerank 可进入 native support matrix，Chat 不默认承诺。
- Midjourney/Suno：本轮不新增伪 native provider，只在 docs/task 中记录为后续 Media provider hardening 承接。

### Conformance 策略

- 对新增 OpenAI-compatible provider，conformance 先冻结为 chat、tools、responses emulation、embeddings 这类低风险 surface。
- 对 object lifecycle、realtime、fine-tuning 等非通用 OpenAI-compatible 能力，继续走 accepted exception，避免把兼容接口误标成生产级全量支持。
- 对 Rerank，Cohere/Jina 标记为 provider-native strategy，但真实上游 smoke 后续拆分。

## 风险

- 不同 provider 的 OpenAI-compatible surface 差异较大，过度声明会导致路由误选。
- provider pricing 经常变化，本轮只记录 pricing metadata source，不写死单价。
- 站点识别基于 base URL 域名，私有化 Dify/代理站点仍可能只能落到 generic。

## 验收标准

- `provider-catalog.json` 至少包含 Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Cohere、Jina、Dify、OpenAI-compatible generic。
- loader 对新增 metadata、重复 code、空 conformance checks 有测试覆盖。
- 管理端 preset response 能返回新增 metadata。
- 公开 docs bundle 能展示新增 provider 支持状态和降级说明。
- conformance/support matrix 对新增 site kind 有测试覆盖，且不把 object lifecycle/realtime 误标为通用兼容。
- 管理端 capability matrix 必须展示 `rerank`，且后端返回的 feature key 与前端展示项一致。
- Jina/Dify 这类非通用 Chat provider 在站点级能力矩阵中不能被默认误判为全量 chat/tools/image/reasoning native。

## 重新打开原因

2026-05-06 复核发现当前实现已完成 catalog 与部分后端能力声明，但仍存在闭环缺口：

- `Moonshot`、`SiliconFlow`、`Volcengine`、`MiniMax`、`Dify`、`Jina` 缺少 conformance fixture 覆盖。
- 后端 capability matrix 返回 `RERANK`，但前端 `MATRIX_FEATURES` 没有展示 `rerank`。
- 前端 matrix 声明了 `chat_text`、`tools`、`image_input`、`file_input`、`reasoning`，后端 `buildFeatureViews` 未返回这些 key。
- 站点级 `SiteCapabilityTruthService.resolve(siteProfile, snapshot, feature)` 默认把多个模型级布尔值传成 `true`，对 Jina/Dify 容易产生能力误判。
- 管理端尚未消费 provider preset catalog metadata，`supportStrategy`、`modelFamilies`、`pricingMetadata`、`unsupportedFeatures` 只停留在 API response。

## 实现结果

- 扩展 `provider-catalog.json` 为多 provider/preset 矩阵，新增 Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Dify、Jina、Cohere、Together、Fireworks、Mistral 等高价值 preset，并为每个 preset 明确 `compatibilitySurface`、`supportStrategy`、`modelFamilies`、`pricingMetadata`、`unsupportedFeatures`。
- 扩展 `ProviderPresetDefinition`、`ProviderCatalogLoader`、`ProviderSitePresetResponse`、`ProviderSiteRegistryService`，让 catalog metadata 能被 loader、管理端 preset 列表和公开 docs bundle 统一消费。
- 补齐 `UpstreamSiteKind`、`UpstreamSitePolicyService`、`ExecutionSupportMatrixService`、`SiteCapabilityTruthService` 对新增 provider 的站点识别、能力判定、accepted degradation 和 blocker reason。
- 扩展 `PublicDocsBundleService` 与 `PublicDocsBundleResponse`，公开 docs bundle 可直接返回 provider preset 支持矩阵，并新增 `rerank` 兼容 protocol 说明。
- 在 `CredentialModelDiscoveryService` 中补齐 Volcengine model discovery path 特例，避免 OpenAI-compatible provider 仍按通用 `/v1/models` 误探测。
- 补齐 `ProviderSiteAdminService.buildFeatureViews` 与前端 `MATRIX_FEATURES` 的 feature key 对齐，管理端 capability matrix 可展示 `rerank`、video/music/web_search 等新增能力状态。
- 修复 `SiteCapabilityTruthService.resolve(siteProfile, snapshot, feature)` 的站点级默认能力：Dify 不再误报 tools/image/reasoning/rerank，Jina 不再误报 chat/tools/image/reasoning，但保留 embeddings/rerank native。
- 管理端站点档案页新增 Provider Preset metadata 区域，展示 `supportStrategy`、`modelFamilies`、`pricingMetadata`、`unsupportedFeatures`，并支持一键导入 preset。
- conformance fixture 新增 Moonshot、SiliconFlow、Volcengine、MiniMax、Dify workflow chat、Jina rerank native，覆盖 plan、explain、model visibility 与 execution gate 同步校验。

## 测试/验证情况

- 通过定向测试命令：
  `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogMarketplaceServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests"`。
- 结果：`BUILD SUCCESSFUL`。
- 覆盖点包括：
  - catalog metadata 解析、重复 code 拒绝、空 `conformanceChecks` 拒绝；
  - 管理端 preset 列表返回新增 metadata；
  - public docs bundle 返回 provider preset 与 `rerank` protocol；
  - `Qwen` OpenAI-compatible 能力识别、`Jina` rerank-native 能力识别；
  - conformance fixture 可加载新增 OpenAI-compatible provider。

- 重新打开后的补充验证已通过：
  - `./gradlew test --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests"`
  - `bun run test -- provider-sites-page capability-matrix-page`
  - `bun run typecheck`

## 遗留问题

- 未执行真实 provider 线上 smoke，因为本轮不提交真实 provider key，真实线上连通性需由后续凭证化 smoke 任务承接。

## 后续建议

- 按 provider 拆分真实 smoke：Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Dify、Jina。
- 将 Midjourney/Suno/Video/Music 等媒体 provider 原生 adapter 与生产 hardening 交给后续 Realtime/Media 任务闭环。
