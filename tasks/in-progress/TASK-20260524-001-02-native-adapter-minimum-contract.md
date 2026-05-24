# TASK-20260524-001-02 支持厂商 Native Adapter 最小契约

状态：In Progress  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260524-001](TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)  
上游来源：[REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)

## 背景

用户要求网关对于需要支持的厂商 API 必须具备 native 能力。当前部分厂商仍以 OpenAI-compatible generic profile 或 translation-layer 口径存在，需定义进入核心支持范围的最小 native adapter 契约。

## 目标

- 为 OpenAI、Anthropic、Gemini/Vertex、MiMo、DeepSeek、xAI 定义 native adapter 最小能力。
- 区分同厂商 native passthrough、同协议 compatible native profile 和跨厂商翻译。
- 对 MiMo、DeepSeek、xAI 等 OpenAI-compatible 厂商建立 provider-specific profile，不再混同 generic OpenAI-compatible。

## 非目标

- 不承诺每个厂商所有官方 API。
- 不实现不可公开验证的私有 API。
- 不把 native 缺口用 local fallback 补齐。

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
- 2026-05-24：本任务继续保持进行中，后续仍需把 `smokeClassification` 接入 smoke harness，并补 adapter 级 contract tests。
