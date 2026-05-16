# TASK-20260514-005 官方价格源版本化同步与人工批准快照

状态：Done  
优先级：Medium  
类型：子任务  
父任务：[TASK-20260514-002](../done/TASK-20260514-002-reference-implementation-detail-comparison.md)  
上游来源：[REQ-20260514-002](../../docs/requirements/REQ-20260514-002-reference-implementation-detail-comparison.md)、[REQ-20260514-005](../../docs/requirements/REQ-20260514-005-provider-pricing-versioned-sync-closure.md)、[REP-20260514](../../docs/reports/REP-20260514-reference-implementation-detail-comparison.md)

## 背景

当前项目已有 pricing metadata、一致性测试、provider reference gap 的 pricing sync 状态和本地 smoke 文档。但对照 `new-api-main` 的 pricing/control plane 和 `sub2api-main` 的 billing resolver，当前价格事实源仍偏静态，缺少版本化同步、人工批准、checksum 和漂移审计。

## 目标

- 建立 provider pricing snapshot 表或等价版本化事实源。
- 支持公开价格页、provider metadata API 或 operator-configured snapshot 的来源分类。
- 增加人工批准状态、checksum、lastVerifiedAt、effectiveAt、supersededAt。
- 将 pricing drift 暴露到 Admin UI 或 reference gap API。

## 非目标

- 不自动抓取需要登录或禁止爬取的价格页。
- 不把聚合站上游模型价格固化为单一 provider 单价。
- 不在没有人工确认时自动改生产计费。

## 输入

- `provider-catalog.json` pricing metadata。
- `ProviderReferenceGapService.pricingRows`
- `docs/provider-smoke-pricing-sync.md`
- 计费、billing snapshot、public pricing 页面。

## 输出

- 版本化 pricing snapshot 设计与实现。
- 同步/导入/人工批准流程。
- pricing drift 审计和 UI/API 展示。
- 回归测试和脱敏 smoke 记录。

## 影响范围

- Provider Catalog。
- Billing / cost routing。
- Public Pricing。
- Admin reference gap / pricing sync 页面。

## 依赖

- provider 官方公开价格或 operator 上传快照。
- 账务策略确认。

## 风险

- provider 价格频繁变化，自动同步如果无批准流程会影响账务可信性。
- 不同 provider 的 token、request、image、audio、video 计价单位不同。
- 聚合站价格由上游模型决定，不能简单归一。

## 验收标准

- pricing snapshot 可版本化存储或导入。
- 每条价格有来源、checksum、验证时间和批准状态。
- 生产计费只使用 approved/effective snapshot。
- reference gap 或 Admin UI 能提示价格漂移和待批准项。

## 测试边界

- Pricing snapshot parser / validator 测试。
- Billing resolver 使用 approved snapshot 的测试。
- Reference gap pricing status 测试。
- 缺少外部网络或真实 key 时测试可用 mock snapshot。

## 关联文档

- [REP-20260514](../../docs/reports/REP-20260514-reference-implementation-detail-comparison.md)
- [REQ-20260514-005](../../docs/requirements/REQ-20260514-005-provider-pricing-versioned-sync-closure.md)
- [provider-smoke-pricing-sync](../../docs/provider-smoke-pricing-sync.md)

## 关联任务

- 父任务：[TASK-20260514-002](../done/TASK-20260514-002-reference-implementation-detail-comparison.md)
- 相关任务：[TASK-20260513-005](../done/TASK-20260513-005-provider-media-pricing-reference-gap-closure.md)

## 当前状态

Done。

## 实现结果

- 新增 `ProviderPricingSnapshotService` 和 `ProviderPricingSnapshotView`，从 `ProviderCatalogSnapshot` 派生版本化 pricing snapshot。
- 每个 snapshot 包含 `sourceKind`、`sourceRef`、`snapshotVersion`、`checksum`、`approvalStatus`、`lastVerifiedAt`、`effectiveAt`、`supersededAt`、`driftStatus`、`productionEligible`。
- `ProviderPricingSyncStatusRow` 扩展 snapshot、checksum、approval、effective window、drift 和 production eligibility 字段。
- `ProviderReferenceGapService` 的 pricing sync 行已消费 snapshot：公开价格源为 approved/effective，provider console/operator-configured 与 aggregator pass-through 默认为 pending review。
- `productionEligible(...)` 只返回 `APPROVED` 且处于 effective window 的 snapshot，pending review 不进入生产计费候选。

## 验证结果

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderPricingSnapshotServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderReferenceGapServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderReferenceGapAdminControllerTests"
```

覆盖点：

- Provider Catalog 派生 snapshot 的 checksum、版本号和 source kind。
- approved/effective snapshot 才能进入 production eligible。
- Qwen/provider-console 类价格保持 `PENDING_REVIEW`，OpenRouter aggregator 不被固化为生产价格。
- Reference gap API 输出 approval、drift 与 production eligibility。

## 遗留边界

- 本轮不抓取公开价格页，也不访问需要登录的 provider console。
- 后续如果需要自动同步远端价格，应新增受 allowlist、robots/条款和人工批准保护的独立 job。
