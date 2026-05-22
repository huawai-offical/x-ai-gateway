# TASK-20260506-009 Provider 生态广度与 Conformance 完善

状态：Done  
优先级：High  
来源：[REP-20260506 三个参考项目功能完成度复核](../../docs/reports/REP-20260506-reference-feature-completeness-review.md)
详细设计：[REQ-20260506-008 Provider 生态广度与 Conformance 闭环设计](../../docs/requirements/REQ-20260506-008-provider-ecosystem-conformance-closure.md)

## 背景

对照 `new-api-main` 与 `cc-switch-main`，当前 `x-ai-gateway` 已具备 Provider Catalog 与 Marketplace，但内置 preset 数量、真实 adapter 广度、模型定价元数据和 conformance 覆盖仍明显不足。

## 目标

- 扩展 provider catalog，从当前主流 6 个 provider 提升到可运营的多 provider/preset 矩阵。
- 为新增 provider 建立 endpoint、capability、pricing、error mode 与 conformance metadata。
- 明确哪些 provider 只走 OpenAI-compatible，哪些需要原生 adapter。

## 范围

- Provider catalog schema 补字段审计。
- 新增一批高价值 provider/preset：至少覆盖 Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Cohere/Jina Rerank、Dify/OpenAI-compatible generic。
- 对 Rerank、Dify、Midjourney/Suno 类能力给出支持策略：原生、兼容、降级或暂不支持。
- 更新 conformance matrix 与公开兼容文档。

## 非目标

- 不一次性实现所有参考项目 provider。
- 不提交真实 provider key。

## 验收标准

- 新增 provider catalog entries 有 schema 校验与 loader 测试。
- 关键 provider 至少有 route/capability/conformance smoke fixture。
- 公开 docs bundle 与管理端 capability matrix 可展示新增能力状态。
- 对未支持能力给出明确降级原因和后续任务建议。

## 推进记录

- 2026-05-06：从剩余 High backlog 中选为下一项闭环任务，进入详细设计与实现阶段。
- 2026-05-06：复核发现当前 `Done` 偏早，重新迁回 `In Progress`。需补齐新增 provider conformance fixtures、管理端 capability matrix `rerank` 展示、站点级能力误判回归，以及前端 preset metadata 可见性。
- 2026-05-06：补齐后端站点级能力声明、前端矩阵/preset 展示、conformance fixtures 与回归测试后重新归档 `Done`。

## 实现结果

- 扩展 `provider-catalog.json` 为可运营的多 provider preset 矩阵，新增 Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Dify、Jina、Together、Fireworks、Mistral 等 preset，并给出 capability/conformance/pricing metadata。
- 新增 catalog metadata 字段：`compatibilitySurface`、`supportStrategy`、`modelFamilies`、`pricingMetadata`、`unsupportedFeatures`，并贯通 loader、管理端 preset response、公开 docs bundle。
- 在 `UpstreamSiteKind`、`UpstreamSitePolicyService`、`ExecutionSupportMatrixService`、`SiteCapabilityTruthService` 中补齐新增 provider 的站点识别、能力矩阵、accepted degradation 与阻断原因。
- `PublicDocsBundleService` 已对外暴露 provider preset 支持矩阵，并加入 `rerank` protocol 文档。
- `CredentialModelDiscoveryService` 已补齐 Volcengine model discovery path 特例。
- `ProviderSiteAdminService` 的 capability matrix feature map 已与前端 key 对齐，补齐 `chat_text`、`tools`、`image_input`、`file_input`、`reasoning`、`rerank`、`video_generation`、`music_generation`、`web_search` 等展示项。
- `SiteCapabilityTruthService` 已改为按 site kind 生成站点级默认能力，避免 Dify/Jina 被误报为全量 chat/tools/image/reasoning native；`TOOLS` 判定改为尊重 `supportsTools`。
- 管理端站点档案页已展示 provider preset metadata，并支持通过 `/admin/provider-sites/presets/{code}/import` 一键导入 preset。
- `site-conformance-fixtures.json` 新增 Moonshot、SiliconFlow、Volcengine、MiniMax、Dify workflow chat、Jina rerank native 覆盖。

## 验证情况

- 通过定向测试：
  `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogMarketplaceServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests"`。
- 结果：`BUILD SUCCESSFUL`。
- 重新打开后的补充验证已通过：
  - `./gradlew test --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests"`
  - `bun run test -- provider-sites-page capability-matrix-page`
  - `bun run typecheck`

## 遗留问题

- 未执行真实 provider 线上 smoke，因为本任务非目标是不提交真实 provider key。后续应按 provider 凭证拆分线上 smoke。

## 后续建议

- 将 Midjourney/Suno/Video/Music 等媒体 provider 原生 adapter 与真实 smoke 拆到 `TASK-20260506-011 Realtime 与 Media 生产硬化` 或独立任务。
- 为 Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Dify、Jina 分别补生产凭证 smoke 与价格同步自动化。

## 2026-05-21 历史归档口径

- 本任务中的 `provider-sites`、`capability matrix`、`站点档案` 页面属于当时的控制台实现背景。
- 当前保留该任务是为了保存 provider 识别、能力判定与 conformance 规则的实现证据，不再代表这些控制台页面仍是现役产品面。
