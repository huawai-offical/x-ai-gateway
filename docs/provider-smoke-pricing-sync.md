# Provider Smoke 与 Pricing Sync

状态：Active  
关联需求：[REQ-20260506-013](requirements/REQ-20260506-013-provider-smoke-pricing-sync.md)  
关联任务：[TASK-20260506-017](../tasks/done/TASK-20260506-017-provider-smoke-pricing-sync.md)、[TASK-20260514-005](../tasks/done/TASK-20260514-005-provider-pricing-versioned-sync.md)

## 目标

Provider Catalog 已具备 18 个 preset。本页记录真实 provider smoke 与 pricing metadata 校验的本地执行方式，保证真实凭证只通过环境变量进入测试进程，不落盘、不入库、不进入报告。

## 安全原则

- 不把真实 provider key 写入源码、文档、任务、配置、测试快照或 Git。
- Smoke 报告只输出 `sha256` 前缀引用，不输出明文 key。
- 免费测试 key 只跑低频短 prompt，不做压测、不循环消耗、不上传大文件。
- 缺少某个 provider key 时，真实 smoke 应记录为 `SKIPPED`，不阻塞普通测试。

## Gemini / Google AI Studio Smoke

默认不执行真实网络请求。需要手动设置：

```powershell
$env:XAG_SMOKE_GEMINI = "true"
$env:XAG_SMOKE_GEMINI_API_KEYS = "<用分号或换行分隔测试 key>"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.provider.adapter.gemini.GeminiProviderSmokeHarnessTests"
Remove-Item Env:XAG_SMOKE_GEMINI
Remove-Item Env:XAG_SMOKE_GEMINI_API_KEYS
```

可选参数：

```powershell
$env:XAG_SMOKE_GEMINI_MODEL = "gemini-2.5-flash-lite"
$env:XAG_SMOKE_GEMINI_MAX_KEYS = "1"
$env:XAG_SMOKE_GEMINI_BASE_URL = "https://generativelanguage.googleapis.com"
```

报告输出：

```text
build/reports/xag-smoke/gemini-ai-studio.md
```

报告字段只包含：

- `keyRef`：测试 key 的 `sha256` 前缀引用。
- `model`：实际选中的 Gemini model。
- `modelsList`：`models.list` 状态分类。
- `generateContent`：最小生成请求状态分类。
- `detail`：脱敏摘要，例如响应文本长度或错误状态枚举。

## Pricing 与一致性校验

常规测试会校验：

- 每个 provider preset 都有 `costProfile`。
- 每个 provider preset 都有 `pricingMetadata`。
- 每个 provider preset 都有 `capabilityTags` 与 `conformanceChecks`。
- Public docs bundle 能暴露非 deprecated preset。
- Conformance fixture 覆盖 catalog 中的 site kind。
- Provider Catalog pricing metadata 会派生为版本化 snapshot，包含 `snapshotVersion`、`checksum`、`approvalStatus`、`effectiveAt`、`supersededAt`、`driftStatus` 和 `productionEligible`。
- 生产计费候选只能来自 `APPROVED` 且处于 effective window 的 snapshot；`provider-console`、`operator-configured` 和 `aggregator-pass-through` 默认需要人工批准。

执行：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderPricingSnapshotServiceTests"
```

## 后续扩展

- Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax 可按 OpenAI-compatible smoke 模板扩展。
- Jina/Cohere 可按 Rerank smoke 模板扩展。
- Dify 可按 workflow-compatible smoke 模板扩展。
- 版本化 pricing snapshot 已具备本地事实源；后续可新增受 allowlist、条款和人工批准保护的远端价格同步 job。
