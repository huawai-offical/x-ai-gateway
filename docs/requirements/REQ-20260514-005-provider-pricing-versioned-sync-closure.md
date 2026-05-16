# REQ-20260514-005 官方价格源版本化同步与人工批准快照闭环

## 背景

`TASK-20260514-005` 来自 `REP-20260514` 的实现细节复核。当前项目已有 `provider-catalog.json` 的 `pricingMetadata`、Provider Reference Gap 的 pricing status 和 smoke 文档；但对照 `new-api-main` 的模型价格/倍率控制面，以及 `sub2api-main` 从远端价格源、hash、fallback file 到 billing resolver 的链路，当前仍缺少可追踪的版本、checksum、人工批准和漂移状态。

## 目标

- 建立 provider pricing snapshot 的版本化事实源，至少从当前 Provider Catalog 派生出可校验快照。
- 每条快照包含 source kind、source ref、checksum、lastVerifiedAt、effectiveAt、supersededAt、approvalStatus、driftStatus。
- reference gap pricing sync 输出这些字段，让 Admin UI/API 能提示待批准和漂移。
- 明确 production billing 只能消费 `APPROVED` 且处于 effective window 的 snapshot。

## 非目标

- 不自动抓取需要登录、禁止爬取或必须通过控制台查看的价格页。
- 不把 OpenRouter/SiliconFlow 等聚合站价格固化为单一 provider 单价。
- 不在本轮改写用户余额、订单或套餐计费模型。

## 方案

1. 新增 `ProviderPricingSnapshotService`，从 `ProviderCatalogSnapshot` 派生版本化 pricing snapshot。
2. 使用稳定字段计算 SHA-256 checksum，形成 `catalogVersion:providerCode:checksum` 版本号。
3. 根据 `pricingMetadata` 归类 source kind 和默认审批状态：公开价格源可标记为 approved/effective，provider console/operator configured 标记为 pending review。
4. 扩展 `ProviderPricingSyncStatusRow`，在 reference gap API 中输出 snapshot、checksum、approval、effective、drift 和 production eligibility。
5. 补充单元测试，验证 checksum 稳定、待审批项不进入 production eligible 列表、reference gap 暴露审批与漂移字段。

## 范围

- `ProviderPricingSnapshotService`
- `ProviderPricingSnapshotView`
- `ProviderPricingSyncStatusRow`
- `ProviderReferenceGapService`
- `ProviderReferenceGapServiceTests`
- `ProviderReferenceGapAdminControllerTests`
- `docs/provider-smoke-pricing-sync.md`
- `tasks/in-progress/TASK-20260514-005-provider-pricing-versioned-sync.md`

## 风险

- catalog 派生 snapshot 仍不是实时价格同步器，只能作为版本化事实源的本地闭环。
- provider console/operator-configured 的价格需要人工确认，不能自动进入生产计费。
- 聚合站价格由具体上游模型决定，本轮只标记 pass-through，不生成单一固定价格。

## 验收标准

- reference gap pricing row 输出 snapshot version、checksum、approvalStatus、effectiveAt、supersededAt、driftStatus、productionEligible。
- service 能区分 approved/effective 与 pending review，不让 pending review 进入 production eligible。
- provider console/operator-configured source 默认需要人工批准。
- 定向测试通过并回写文档/任务。

## 关联任务

- [TASK-20260514-005 官方价格源版本化同步与人工批准快照](../../tasks/done/TASK-20260514-005-provider-pricing-versioned-sync.md)
- [TASK-20260514-002 参考项目实现细节深度对比](../../tasks/done/TASK-20260514-002-reference-implementation-detail-comparison.md)

## 状态

Done。已完成 Provider Catalog 派生的版本化 pricing snapshot、checksum、approval/effective gate、reference gap pricing drift 展示和定向回归验证。
