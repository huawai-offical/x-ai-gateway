# TASK-20260524-001-01 核心厂商目录收敛与非模型 Provider 清理

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260524-001](../in-progress/TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)  
上游来源：[REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)

## 背景

当前 `provider-catalog.json` 包含 Dify、OpenRouter、Together、Fireworks、SiliconFlow 等非核心、聚合型或非自有模型 provider。用户要求删除很多无意义厂商，保留 xAI 以及拥有自研大模型、native API 或 provider-specific native profile 的头部厂商。新的产品口径进一步明确：Dify 等非自有模型厂商不应进入默认核心能力承诺，不能通过 compatible、emulated、degraded 或 metadata/header 标记伪装为可用核心厂商。

## 目标

- 审计现有 provider preset，按核心保留、候选保留、默认清理分类。
- 从默认核心导入范围移除 Dify 等非自有模型编排平台或聚合器。
- 明确 xAI、OpenAI、Anthropic、Gemini/Vertex、MiMo、DeepSeek 的保留口径。
- 对 Qwen、Moonshot、MiniMax、Mistral、Cohere、Jina、Perplexity 等候选给出保留或降级为可选 preset 的依据。

## 非目标

- 不删除用户已有凭证数据。
- 不一次性实现所有候选厂商 native adapter。
- 不把聚合器作为核心默认厂商。
- 不用 emulation、degraded 或 generic compatible alias 把非自有模型厂商重新包装成核心厂商。

## 输入

- `src/main/resources/provider-catalog.json`
- 默认厂商导入逻辑。
- 厂商管理 UI 与 provider catalog loader tests。

## 输出

- 更新后的核心厂商清单和 provider preset 分类。
- Dify 等清理/降级说明。
- 对存量凭证和测试 fixture 的兼容策略。
- 默认核心厂商必须以 native API/profile 或可证明无损互转作为后续支持前提。

## 影响范围

- provider catalog JSON。
- provider preset loader。
- 默认厂商 API 入口引导。
- 厂商管理界面和文档。

## 依赖

- TASK-20260523-013 厂商目录 UI 收敛。
- TASK-20260523-014 厂商领域 API 命名边界。

## 风险

- 删除 preset 可能让已有测试引用失效。
- 聚合器可能仍有客户使用场景，需要明确是否转入 optional/backlog。
- 若保留历史 site kind 或协议枚举，必须避免这些历史兼容入口重新出现在默认核心 preset、smoke 或公开支持承诺中。

## 验收标准

- Dify 不再作为默认核心 provider preset。
- 核心厂商和候选厂商分类明确。
- provider catalog loader tests 覆盖核心厂商存在、清理厂商缺省不导入。
- 非自有模型厂商不可通过 generic OpenAI-compatible、degraded、emulated 或 metadata/header 标记进入默认核心支持。

## 测试边界

- `ProviderCatalogLoaderTests`
- 厂商管理相关 snapshot/API 测试。

## 当前状态

- 2026-05-24：进入实施。默认核心 provider catalog 收敛为 OpenAI、Azure OpenAI、MiMo、DeepSeek、Qwen、Moonshot、Volcengine、MiniMax、xAI、Perplexity、Cohere、Jina、Mistral、Anthropic、Gemini、Vertex。
- 2026-05-24：已从默认核心 catalog 移除 `openai_compatible_generic`、`dify`、`openrouter`、`siliconflow`、`together`、`fireworks`；这些 site kind/协议枚举暂不删除，用于兼容历史数据、迁移记录和后续 optional/backlog 决策。
- 2026-05-24：已同步 `ProviderCatalogLoader` builtin fallback，避免 classpath catalog 异常时重新暴露 OpenRouter；补充 provider catalog、public docs、pricing snapshot、preset import 相关测试边界。
- 2026-05-24：验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderPricingSnapshotServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests"`。
- 2026-05-24：子任务完成。后续 `TASK-20260524-001-02/03/04/05` 继续承接 native adapter、无损翻译矩阵、不可对应能力失败语义和 OpenAPI/smoke 全量对齐。
- 2026-05-24：按最新产品口径补充归档说明：本任务 Done 状态保持不变，完成边界是默认核心 catalog 清理；后续不允许 Dify 等非自有模型厂商通过 emulation/degraded/generic alias 重新进入核心支持，xAI 保留在头部厂商范围内。
