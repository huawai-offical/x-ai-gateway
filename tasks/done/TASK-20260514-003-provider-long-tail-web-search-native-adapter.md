# TASK-20260514-003 Provider 长尾 Preset、Web/Search 与 Native Adapter 追平

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-002](../done/TASK-20260514-002-reference-implementation-detail-comparison.md)  
上游来源：[REQ-20260514-002](../../docs/requirements/REQ-20260514-002-reference-implementation-detail-comparison.md)、[REP-20260514](../../docs/reports/REP-20260514-reference-implementation-detail-comparison.md)

## 背景

对比 `new-api-main/relay/channel` 后，当前项目在云端治理能力上更强，但 provider/channel 原生覆盖宽度尚未完全追平。`ProviderReferenceGapService` 已把 xAI、Perplexity、Vertex、AWS Bedrock、Baidu、Zhipu、Tencent、Cloudflare、Replicate、Xinference 等标记为缺口或独立建模对象。

## 目标

- 为高价值长尾 provider 增补 catalog preset、auth 方式、model discovery、pricing source、conformance fixture。
- 对 Perplexity / xAI / web_search 类能力明确 `web_search` 语义、provider-specific 参数和响应归一边界。
- 对 Vertex / Bedrock 这类 native path provider 明确 project/location、service account、SigV4 等认证和路径 adapter。
- 增加可选真实 smoke 入口，缺少真实 key 时必须 skip 并输出脱敏原因。

## 非目标

- 不一次性实现所有参考项目 channel。
- 不把不稳定或非公开接口伪装成生产级 supported。
- 不提交真实 secret。

## 输入

- `src/main/resources/provider-catalog.json`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/ProviderReferenceGapService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/*`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/catalog/CredentialModelDiscoveryService.java`
- 参考项目 `new-api-main/relay/channel/*`

## 输出

- 新增或更新 provider preset。
- Web/Search 与 native provider adapter 边界文档。
- Conformance fixture 和 provider reference gap 矩阵更新。
- 可选真实 smoke 报告格式。

## 实现结果

- `provider-catalog.json` 新增 `xai`、`perplexity`、`vertex` preset，并补齐 capability tags、pricing metadata、unsupported features 和 conformance checks。
- 新增 `PERPLEXITY` site kind，`UpstreamSitePolicyService` 可根据 `api.perplexity.ai` 自动识别。
- `SiteCapabilityTruthService` 与 `ExecutionSupportMatrixService` 只允许 `OPENAI_DIRECT` 和 `PERPLEXITY` 具备 `WEB_SEARCH`，通用 OpenAI-compatible 站点保持阻断。
- 新增 `PerplexityWebSearchAdapter`，把网关 `/v1/web_search` payload 转为 Perplexity Sonar `/v1/sonar` 形态，并透传 search filter 类参数。
- `ProviderReferenceGapService` 中 xAI、Perplexity、Vertex 从 `MISSING` 收敛为 `SUPPORTED`；`web_search` media row 从 `PLANNED` 收敛为 `PROVIDER_ADAPTER`。
- `PublicDocsBundleService` 与 `docs/openapi/public-openapi.json` 增加 `/v1/web_search`，公开 docs provider presets 自动包含三组新增 provider。
- 新增 [provider-long-tail-web-search-native-adapter](../../docs/provider-long-tail-web-search-native-adapter.md) 作为 adapter 边界与验证证据文档。

## 影响范围

- Provider Catalog。
- Provider Site / Capability Matrix。
- Native Compatibility / Reference Gap 页面。
- Public Docs compatibility bundle。

## 依赖

- 目标 provider 的官方文档或本地参考项目实现。
- 可选测试 key 或 mock server。

## 风险

- 长尾 provider 的 OpenAI-compatible surface 差异大，过度声明会导致错误路由。
- Web/Search 返回 citation、source、tool 事件格式差异大，需要显式降级。
- Vertex / Bedrock 认证比普通 API key 更复杂。

## 验收标准

- 至少完成一批高价值 provider 的 catalog/conformance/preset 更新。
- Gap matrix 中对应 provider 状态从笼统 `MISSING` 收敛为 `SUPPORTED`、`COMPATIBLE` 或明确的 `ADAPTER_REQUIRED`。
- Web/Search provider 不再只停留在 canonical path，必须有 adapter 边界和测试证据。
- 缺少真实 key 时 smoke 输出 `SKIPPED`，不失败阻断。

## 测试边界

- Provider catalog loader 测试。
- Native compatibility / reference gap service 测试。
- Web/Search canonical semantics 测试。
- 可选真实 provider smoke。

## 验证结果

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderReferenceGapServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.NativeCompatibilityServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.PerplexityWebSearchAdapterTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"
```

结果：通过。

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiStyleResourceExecutorTests"
```

结果：通过。

## 关联文档

- [REQ-20260514-003](../../docs/requirements/REQ-20260514-003-provider-long-tail-web-search-native-adapter-closure.md)
- [provider-long-tail-web-search-native-adapter](../../docs/provider-long-tail-web-search-native-adapter.md)
- [REP-20260514](../../docs/reports/REP-20260514-reference-implementation-detail-comparison.md)
- [provider-smoke-pricing-sync](../../docs/provider-smoke-pricing-sync.md)
- [public-api-compatibility](../../docs/public-api-compatibility.md)

## 关联任务

- 父任务：[TASK-20260514-002](../done/TASK-20260514-002-reference-implementation-detail-comparison.md)
- 相关任务：[TASK-20260513-005](../done/TASK-20260513-005-provider-media-pricing-reference-gap-closure.md)

## 当前状态

Done。本批已闭环 xAI、Perplexity、Vertex 三个高价值 provider，其中 Perplexity 已有 `web_search` provider-specific adapter；AWS Bedrock、Baidu、Zhipu、Tencent、Cloudflare、Replicate、Xinference 等继续保留在后续 backlog/任务边界内。
