# TASK-20260524-001-02 支持厂商 Native Adapter 最小契约

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260524-001](../in-progress/TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)  
上游来源：[REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)

## 背景

用户要求网关对于需要支持的厂商 API 必须具备 native 能力。当前产品定义只承诺头部自研模型厂商 native API / provider-specific native profile 与可证明无损翻译；Dify 等非自有模型厂商是非目标，xAI 保留。当前部分厂商仍以 OpenAI-compatible generic profile 或 translation-layer 口径存在，需定义进入核心支持范围的最小 native adapter 契约，并确保不可映射、不可无损或非 native 能力直接 hard-fail。

## 目标

- 为 OpenAI、Anthropic、Gemini/Vertex、MiMo、DeepSeek、xAI 定义 native adapter 最小能力。
- 区分同厂商 native passthrough、同协议 compatible native profile 和跨厂商翻译。
- 对 MiMo、DeepSeek、xAI 等 OpenAI-compatible 厂商建立 provider-specific profile，不再混同 generic OpenAI-compatible。

## 非目标

- 不承诺每个厂商所有官方 API。
- 不实现不可公开验证的私有 API。
- 不把 native 缺口用 local fallback、emulation、degraded 成功、local fake、模拟返回或 metadata/header 标记补齐。

## 输入

- 现有 provider adapter 包。
- protocol endpoint conversation profile。
- provider catalog capability metadata。

## 输出

- native adapter contract 文档。
- 每个核心厂商的必选 endpoint、auth、stream、tools、usage、error mapping 最小要求。
- native 缺口 backlog。

## 影响范围

- `provider.adapter.*`
- Gateway runtime executors。
- capability matrix、conformance matrix、smoke harness。

## 依赖

- 核心厂商目录收敛任务。
- 真实 smoke key 与 record/replay fixture。

## 风险

- 厂商 API 更新可能导致 native 契约漂移。
- MiMo/DeepSeek/xAI 的 OpenAI-compatible 差异若未独立建模，会在运行时误判能力。

## 验收标准

- 每个核心厂商都有 native adapter 最小契约。
- 缺失 native 的能力不能进入支持矩阵成功路径。
- 仅有 generic OpenAI-compatible 口径且无法证明 provider-specific native profile 的能力必须标为 unsupported 或拆出阻断任务。
- smoke harness 可按厂商 native contract 分类 PASS/FAIL/UNSUPPORTED。

## 测试边界

- adapter 单元测试。
- functional provider smoke dry-run。
- 真实 smoke 在有 key 和预算时执行。

## 当前状态

- 2026-05-24：进入实施。本轮先把 native adapter 最小契约落成 provider catalog 的结构化事实源：核心厂商必须声明 adapter kind、native protocols、required endpoints、auth、stream、tools、usage、error mapping 和 smoke classification；后续再接 smoke harness 与更细 adapter 单元测试。
- 2026-05-24：已新增 `nativeAdapterContract` 结构化字段，并从 `ProviderCatalogLoader`、Admin preset response、Public docs provider preset response 透出。
- 2026-05-24：已为默认 16 个核心 provider preset 填写最小契约，覆盖 adapter kind、native surface、native protocols、required endpoints、auth、stream、tools、usage、error mapping、smoke classification 和关键 translation boundary。
- 2026-05-24：已新增报告 [REP-20260524-001](../../docs/reports/REP-20260524-001-native-adapter-minimum-contract.md)，作为 human-readable contract 摘要。
- 2026-05-24：验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests"`。
- 2026-05-24：此前遗留的 `smokeClassification`、adapter contract tests 与 hard-fail 守门已分别由本任务 smoke contract drift 切片、`001-06`、`001-08` 和 `001-03` 承接；本任务不再保留未归属缺口。
- 2026-05-24：补充 native adapter 现状审计：OpenAI/Anthropic/Gemini 已有真实 runtime adapter；MiMo、DeepSeek、xAI、Qwen、Moonshot、Volcengine、MiniMax、Mistral、Perplexity 当前仍主要是 provider-specific OpenAI-compatible contract，runtime 身份仍共用 `OPENAI_COMPATIBLE`；Cohere/Jina 已有 native contract 但 executor/smoke 证据未闭环。
- 2026-05-24：已拆分后续任务：[TASK-20260524-001-06](TASK-20260524-001-06-provider-specific-runtime-profile-split.md) 已完成 provider-specific runtime profile；[TASK-20260524-001-07](../in-progress/TASK-20260524-001-07-native-executor-smoke-for-embed-rerank-providers.md) 处理 Cohere/Jina native executor 与真实 smoke；[TASK-20260524-001-03](TASK-20260524-001-03-lossless-translation-matrix.md) 已完成跨协议 mapper negative tests 与 UNSUPPORTED smoke 样本。
- 2026-05-24：按当前网关定义完成验收口径回收：native adapter contract 不能把 non-native、不可映射或不可无损能力包装成可用；provider-specific runtime profile 与 hard-fail 守门已由 `001-06` / `001-08` 闭环，剩余跨协议矩阵证据与 Cohere/Jina live smoke 分别保留在 `001-03` / `001-07`。

## Smoke Contract Drift 切片 Task Spec

### 背景

`nativeAdapterContract.requiredEndpoints` 已经成为 provider catalog 事实源，但 functional provider smoke 仍主要靠硬编码协议、baseUrl 与默认 `/v1/chat/completions` 推断。Qwen、Volcengine、Perplexity、Vertex 等 provider-specific native profile 有 path adapter 或 project/location 寻址要求，如果 smoke dry-run 不读取或覆盖这些差异，就会出现 catalog 宣称 native/profile contract、smoke 却用 generic OpenAI-compatible path 验证的漂移。

### 目标

- functional provider smoke 能识别默认核心 provider-specific OpenAI-compatible 厂商的具名协议。
- dry-run 生成的 path/baseUrl 与 catalog `nativeAdapterContract.requiredEndpoints` 保持一致。
- 继续拒绝把 Dify/OpenRouter/Together/Fireworks/SiliconFlow/generic compatible 当作 official smoke 证据。

### 非目标

- 不新增数据库 enum 或持久化 runtime provider key。
- 不执行真实 live smoke。
- 不把 provider-specific chat contract 自动扩展到 files/uploads/realtime/batches 等其它官方 API。

### 输入

- `src/main/resources/provider-catalog.json`
- `FunctionalProviderSmokeHttpClient`
- `FunctionalProviderSmokeHttpClientTests`

### 输出

- provider-specific OpenAI-compatible smoke 协议与默认 baseUrl/path/model 覆盖。
- contract drift 测试，证明 catalog required endpoint 与 dry-run path 一致。

### 当前状态

- 2026-05-24：进入实施。已识别 Qwen、Moonshot、Volcengine、MiniMax、Mistral、Perplexity 的 `requiredEndpoints` 不能继续由 generic `/v1/chat/completions` 推断，先补 functional provider smoke provider-specific 协议与 contract-driven tests。
- 2026-05-24：切片实现完成。`FunctionalProviderSmokeHttpClient` 已支持 `QWEN_OPENAI_COMPATIBLE`、`MOONSHOT_OPENAI_COMPATIBLE`、`VOLCENGINE_OPENAI_COMPATIBLE`、`MINIMAX_OPENAI_COMPATIBLE`、`MISTRAL_OPENAI_COMPATIBLE`、`PERPLEXITY_OPENAI_COMPATIBLE`，按各自 catalog baseUrl/path adapter 生成 dry-run 和 live probe；MiMo OpenAI-compatible smoke 已按 catalog `auth=bearer` 收敛，不再使用旧 `api-key` header。
- 2026-05-24：`FunctionalProviderSmokeRecordReplayFixtureVerifier` 与 certification 已扩展核心 provider-specific protocol 白名单，离线 fixture 不再接受 generic `OPENAI_COMPATIBLE` 作为 official smoke 证据；样本 fixture 已同步 Bearer 脱敏口径。
- 2026-05-24：验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests"`。
- 2026-05-24：扩展验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.shared.ProviderRuntimeProfileTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests"`。
- 2026-05-24：归档。本任务完成 native adapter 最小契约、provider catalog 事实源、public/Admin 透出、provider-specific smoke protocol/path 对齐与验证回写；未完成的跨协议无损矩阵证据和 Cohere/Jina live smoke 已由 `001-03`、`001-07` 继续承接。
