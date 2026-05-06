# TASK-20260506-017 Provider 真实凭证 Smoke 与价格同步自动化

状态：Done  
优先级：High  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-013 Provider 真实 Smoke 与价格同步闭环](../../docs/requirements/REQ-20260506-013-provider-smoke-pricing-sync.md)

## 背景

Provider Catalog 已扩展到 18 个 preset，并补齐 metadata、capability matrix 和 conformance fixture。但对照 `new-api-main` 的大量 channel/relay，当前仍缺真实 provider 凭证 smoke、价格同步自动化和每个 provider 的生产可用证据。

用户已提供专门用于测试的 Google AI Studio API key。本任务优先实现 Gemini/Google AI Studio 真实 smoke；其他 provider 暂按 credential optional 策略处理，缺 key 时记录为 `SKIPPED`。

## 目标

- 为 Google AI Studio/Gemini 建立可选真实 smoke harness。
- 为 Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Dify、Cohere/Jina 等新增 provider 预留可选真实 smoke 报告格式。
- 建立价格元数据同步或快照校验流程，减少手工维护漂移。
- 将 smoke 结果以脱敏报告落到本地 build report。

## 范围

- Provider smoke 配置 schema 与本地环境变量约定。
- Gemini `models.list`、最小 `generateContent`、错误归一和低频报告。
- Chat、Rerank、Dify workflow、model discovery、错误码归一和计费 metadata 的后续扩展点。
- pricing metadata 快照校验。
- provider catalog、public docs bundle 和 capability matrix 的一致性校验。

## 非目标

- 不提交真实 provider key。
- 不一次性实现所有参考项目 channel。
- 不承诺每个 provider 都有原生 adapter；可接受 OpenAI-compatible 或 accepted degradation。
- 不对免费测试 key 做压测或高 token 消耗。

## 验收标准

- Gemini 有可选真实 smoke 入口和脱敏报告格式。
- 无凭证时 smoke 自动 skip，不影响常规测试。
- pricing metadata 有同步脚本或快照校验测试。
- conformance fixture、provider catalog 和公开 docs bundle 之间有一致性校验。
- 失败 smoke 能区分凭证缺失、权限失败、限流、网络失败、provider 不支持和 gateway 转换错误。

## 实现记录

- 新增 `GeminiProviderSmokeHarnessTests`，支持 Google AI Studio/Gemini 真实 smoke。
- Smoke 通过 `XAG_SMOKE_GEMINI` 开关控制；无开关或无 key 时自动 skip。
- 支持 `XAG_SMOKE_GEMINI_API_KEYS`、`GOOGLE_AI_STUDIO_API_KEYS`、`GEMINI_API_KEY` 多环境变量读取。
- 支持 `XAG_SMOKE_GEMINI_MODEL` 指定模型；未指定时通过 `models.list` 自动选择支持 `generateContent` 的模型。
- 支持 `XAG_SMOKE_GEMINI_MAX_KEYS` 限制本次执行 key 数量，避免免费 key 被过度消耗。
- Smoke 失败分类覆盖 `auth_failed`、`rate_limited`、`provider_unsupported`、`upstream_error`、`request_invalid`、`network_or_client_error`。
- 新增 `SmokeHarnessSupport.envList` 与 `SmokeHarnessSupport.secretRef`，报告只输出 `sha256` 前缀引用。
- `ProviderCatalogLoaderTests` 增加 pricing metadata、public docs bundle 与 conformance fixture 一致性校验。
- 新增 [docs/provider-smoke-pricing-sync.md](../../docs/provider-smoke-pricing-sync.md) 记录本地安全执行方式。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiProviderSmokeHarnessTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"
```

覆盖点：

- 无 smoke 开关时 Gemini 真实 smoke 自动 skip。
- Provider Catalog 每个 preset 的 `costProfile`、`pricingMetadata`、`capabilityTags`、`conformanceChecks` 非空。
- Public docs bundle 暴露 catalog 中的非 deprecated preset。
- Conformance fixture 覆盖 catalog 中的 site kind。

## 遗留问题

- 当前工具进程没有执行真实远程 smoke，因为测试 key 不写入命令、文件或报告；需要在本机 PowerShell 中设置环境变量后执行。
- Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Dify、Cohere/Jina 的真实 smoke 仍待后续凭证和模板扩展。
