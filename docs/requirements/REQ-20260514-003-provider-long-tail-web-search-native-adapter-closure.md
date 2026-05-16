# REQ-20260514-003 Provider 长尾 Preset、Web/Search 与 Native Adapter 闭环

## 背景

`REP-20260514` 对比参考项目 `new-api-main/relay/channel` 后确认：当前项目在账号治理、路由策略、账务和 Portal/Console 产品面上已经明显强于参考项目，但 provider/channel 原生覆盖广度仍有缺口。`TASK-20260514-003` 聚焦第一批高价值长尾 provider，把已经有基础枚举或 native path 能力的对象从笼统 `MISSING` 收敛成可导入、可解释、可测试的 catalog / conformance / gap matrix 状态。

## 目标

- 增补 xAI、Perplexity、Vertex 的 Provider Catalog preset，并补齐 capability tags、pricing metadata、unsupported features 和 conformance checks。
- 为 Perplexity 建立独立 site kind 与 `web_search` 能力边界，避免把所有 OpenAI-compatible provider 自动宣称为 web search provider。
- 将 Vertex 从参考缺口收敛为 `google_native` preset，明确 project/location/service account 类 native path 仍与 Gemini AI Studio 分开治理。
- 更新 reference gap、native compatibility、public docs 与 conformance fixture，使页面和 API 输出能体现真实支持状态。

## 非目标

- 不一次性补齐 AWS Bedrock、Baidu、Zhipu、Tencent、Cloudflare、Replicate、Xinference 等全部缺口。
- 不提交真实 provider key，不把真实 smoke 失败作为本地单元测试阻断。
- 不声明 Perplexity 等价于 OpenAI `/v1/web_search` 的完整响应契约；本批只冻结 provider-specific adapter 边界与可路由能力。

## 方案

1. `provider-catalog.json` 增加 `xai`、`perplexity`、`vertex` preset。
2. `UpstreamSiteKind` 增加 `PERPLEXITY`，`UpstreamSitePolicyService` 按 `api.perplexity.ai` 自动识别，并使用 bearer + OpenAI v1 path policy。
3. `ExecutionSupportMatrixService` 与 `SiteCapabilityTruthService` 仅对 `OPENAI_DIRECT` 和 `PERPLEXITY` 开放 `WEB_SEARCH`，对通用兼容站点保持阻断。
4. `ProviderReferenceGapService` 将 xAI / Perplexity / Vertex 从 `MISSING` 收敛到 `SUPPORTED` 或 provider adapter 状态，并把 `web_search` media row 从 `PLANNED` 调整为有 provider preset 的 adapter 面。
5. 增加 conformance fixture 与测试断言，确保 catalog、gap matrix、native compatibility、public OpenAPI 同步。

## 范围

- `src/main/resources/provider-catalog.json`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/shared/UpstreamSiteKind.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/site/UpstreamSitePolicyService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/*`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/ProviderReferenceGapService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/NativeCompatibilityService.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/test/resources/conformance/site-conformance-fixtures.json`
- 相关单元测试与本地任务文档

## 风险

- Perplexity 当前是 OpenAI Chat Completions 兼容形态，并带 web-grounded 能力；不能把它误建模为 OpenAI 全量 object lifecycle。
- xAI 支持 OpenAI-compatible REST，但部分新能力优先进入 Responses 或专有路径，需用 `unsupportedFeatures` 标清。
- Vertex project/location 路径与 AI Studio Gemini API 不同，后续真实 smoke 需要 service account 或 gcloud token，不能复用 AI Studio key。

## 验收标准

- xAI、Perplexity、Vertex 能从 catalog loader 加载，并出现在 public docs provider presets。
- Gap matrix 中 xAI、Perplexity、Vertex 不再是 `MISSING`。
- `/v1/web_search` 在 public OpenAPI 中可见，reference gap media row 标记 provider adapter，并列出 OpenAI / Perplexity provider presets。
- `PERPLEXITY` 的 `WEB_SEARCH` conformance fixture 可执行；通用 OpenAI-compatible 站点仍不会自动获得 web search 支持。
- 缺少真实 provider key 时，本批只记录 smoke 入口和 `SKIPPED` 边界，不阻断本地测试。

## 测试边界

- `ProviderCatalogLoaderTests`
- `ProviderReferenceGapServiceTests`
- `NativeCompatibilityServiceTests`
- `ExecutionSupportMatrixServiceTests`
- `SiteCapabilityTruthServiceTests`
- `SiteConformanceHarnessTests`
- `PublicDocsBundleServiceTests`

## 关联文档

- [REP-20260514](../reports/REP-20260514-reference-implementation-detail-comparison.md)
- [TASK-20260514-003](../../tasks/done/TASK-20260514-003-provider-long-tail-web-search-native-adapter.md)

## 实现结果

- 新增 `xai`、`perplexity`、`vertex` provider preset。
- 新增 `PERPLEXITY` site kind，并补齐 policy、capability truth、execution support matrix、conformance fixture。
- 新增 `PerplexityWebSearchAdapter`，将 `/v1/web_search` 转为 Perplexity Sonar `/v1/sonar` 请求形态。
- 更新 reference gap、native compatibility、public docs bundle 与本地 OpenAPI。
- 详细边界见 [provider-long-tail-web-search-native-adapter](../provider-long-tail-web-search-native-adapter.md)。

## 验证结果

- Provider catalog / reference gap / native compatibility / Perplexity adapter / capability truth / conformance / public docs 专项测试通过。
- Provider site admin / registry / gateway feature semantics / OpenAI-style resource executor 关联测试通过。

## 当前状态

Done。
