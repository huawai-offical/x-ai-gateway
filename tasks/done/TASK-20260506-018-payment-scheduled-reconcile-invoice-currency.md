# TASK-20260506-018 支付定时对账、订阅发票与跨币种结算

状态：Done  
优先级：High  
来源：[REP-20260506 参考项目功能深度再复核](../../docs/reports/REP-20260506-reference-feature-depth-recheck.md)  
关联需求：[REQ-20260506-014 支付定时对账、订阅发票与跨币种结算](../../docs/requirements/REQ-20260506-014-payment-scheduled-reconcile-invoice-currency.md)

## 背景

当前支付模块已经有 checkout、webhook、退款、争议和主动对账 API。对照 `sub2api-main` 与 `new-api-main` 的用户自助充值和支付运营体验，仍缺定时对账任务、发票税务、复杂订阅计费和跨币种结算。

## 目标

- 将支付从手动运营 API 推进到可调度的生产账务闭环。
- 支持订阅账单、发票记录、税务信息和跨币种结算快照。
- 将对账异常接入运营审计，后续可接入告警。

## 范围

- 定时对账 job、重试、锁和审计的最小 service 闭环。
- provider reconciliation report 与异常审计。
- invoice/tax profile 数据模型草案。
- 订阅周期账单、余额扣减和跨币种汇率快照的最小数据基础。
- Admin 支付运营面补齐。

## 非目标

- 不提交真实商户密钥。
- 不做复杂财税合规承诺，仅建立产品与数据模型基础。
- 不接入无稳定文档的支付渠道。

## 验收标准

- 对账任务可按 provider instance 调度，失败可重试且有审计记录。
- 支付订单、退款、争议、订阅账单和发票记录可关联查询。
- 跨币种金额有汇率快照和幂等校验。
- 对账异常可进入支付审计，后续能扩展到 ops alert 或 system event。

## 实现记录

- 新增 `POST /admin/payments/reconcile/scheduled`，返回 `PaymentScheduledReconcileRunResponse`，包含 run id、窗口、provider、订单数、异常数和原始 report。
- 新增 `PaymentAdminService#runScheduledReconcile`，复用现有 reconcile 逻辑并为异常状态写入 `RECONCILE_ANOMALY` audit。
- 新增默认关闭的 `PaymentReconciliationScheduler`，支持通过配置开启固定间隔对账。
- 扩展 checkout provider payload，订单 metadata 可沉淀：
  - 发票信息：`invoiceNo`、`invoiceTitle`、`invoiceEmail`。
  - 税务信息：`taxProfileCode`、`taxRateBps`、`taxInclusive`。
  - 结算信息：`settlementCurrency`、`exchangeRate`、`baseAmountMinor`、`settlementAmountMinor`。
  - 订阅账单信息：`subscriptionId`、`billingPeriodStart`、`billingPeriodEnd`、`billingCycle`。
- provider 能力列表增加 `scheduled_reconcile`，便于 Admin 前端和公开能力文档后续展示。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.PaymentAdminServiceTests"
```

验证覆盖：

- invoice/tax/settlement/billing 快照写入 provider payload。
- scheduled reconciliation run 生成 `pay_recon_...` run id。
- `PENDING`、`FAILED`、`DISPUTED` 计入异常并写入审计。
- `PAID`、`REFUNDED` 仍保持 `MATCHED` 分类。

## 遗留问题

- 未接入真实 provider 远程查询、真实汇率、真实发票服务。
- 生产启用定时对账前需要补分布式锁或单实例保障，避免多实例重复 run。
- 复杂订阅 prorate、企业合同发票、多税区合规需要后续独立增强任务继续推进。
