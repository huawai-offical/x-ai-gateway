# Provider 长尾 Preset、Web/Search 与 Native Adapter 闭环

## 本批范围

本批闭环 `TASK-20260514-003` 的第一组高价值 provider：

| Provider | Catalog code | Site kind | 支持状态 | Adapter 边界 |
| --- | --- | --- | --- | --- |
| xAI Grok | `xai` | `GROK` | `SUPPORTED` | OpenAI-compatible chat/responses；web search、files 等专有能力只记录边界，不泛化为全量 lifecycle。 |
| Perplexity | `perplexity` | `PERPLEXITY` | `SUPPORTED` | `/v1/web_search` 转换为 Perplexity Sonar `/v1/sonar`，保留 search filter 与 citation/source 返回。 |
| Vertex AI | `vertex` | `VERTEX_AI` | `SUPPORTED` | Google Cloud project/location + bearer/service account native path，与 AI Studio API key surface 分开治理。 |

## Perplexity Web/Search Adapter

- 网关公开入口仍是 `/v1/web_search`。
- 只有 `PERPLEXITY` site kind 会进入 `PerplexityWebSearchAdapter`。
- Adapter 将 `query` 或 `input` 组装为 Sonar `messages`，并把 `search_domain_filter`、`search_recency_filter`、`search_mode`、`return_images`、`return_related_questions` 等搜索参数透传。
- 通用 `OPENAI_COMPATIBLE_GENERIC` 不会继承 `web_search` 能力，避免错误路由。
- 缺少真实 Perplexity key 时，本地测试只验证 adapter shape 与 conformance plan；真实 smoke 后续按环境变量输出 `SKIPPED` 或分类失败。

## Gap Matrix 收敛

这里的 `Gap Matrix` 与 `Native Adapter` 仅指内部服务事实源和协议适配边界；控制台中的 `Provider 参考差距`、`Native 命名空间兼容` 页面已下线，不再作为当前产品入口。

- `ProviderReferenceGapService.providerRows` 中 xAI、Perplexity、Vertex 已从 `MISSING` 收敛为 `SUPPORTED`。
- `mediaRows.web_search` 从 `PLANNED` 收敛为 `PROVIDER_ADAPTER`，provider presets 包含 `openai` 与 `perplexity`。
- `pricingRows` 增加 xAI、Perplexity、Vertex 的公开价格源跟踪占位，真正的版本化同步继续归属 `TASK-20260514-005`。

## 公开文档

- `PublicDocsBundleService` 增加 `web_search` compatibility row。
- `GET /public/docs/openapi.json` 与本地 `docs/openapi/public-openapi.json` 增加 `POST /v1/web_search`。
- Provider presets 自动暴露 `xai`、`perplexity`、`vertex`。

## 验证

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderReferenceGapServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.NativeCompatibilityServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.PerplexityWebSearchAdapterTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"

.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiStyleResourceExecutorTests"
```

两组专项测试均已通过。

## 后续边界

- AWS Bedrock、Baidu、Zhipu、Tencent、Cloudflare、Replicate、Xinference 仍在后续 backlog，不纳入本批。
- 真实 provider key smoke 与价格快照版本化同步继续由 `TASK-20260514-005` 处理。
- 专有 Video/Music adapter 与产物级 smoke 继续由 `TASK-20260514-004` 处理。
