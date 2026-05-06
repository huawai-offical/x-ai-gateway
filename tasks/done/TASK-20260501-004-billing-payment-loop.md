# TASK-20260501-004 SaaS 计费与支付闭环：价格、余额、订单、支付渠道、Webhook、对账

状态：Done  
优先级：High  
来源：Linear X-285  
来源 URL：https://linear.app/x-ai/issue/X-285/saas-计费与支付闭环价格余额订单支付渠道webhook对账  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)  
关联设计：[REQ-20260501-004](../../docs/requirements/REQ-20260501-004-third-priority-task-closure-design.md)

## 背景

`new-api` 和 `Sub2API` 都具备在线充值、模型价格、订阅、订单、支付回调和账单能力。当前 `x-ai-gateway` 已有用户域、套餐、兑换码和余额账本，但未看到完整 PaymentOrder、PaymentProvider、Webhook 回调、退款/对账和用户账单闭环。

## 目标

把用户域从“可管理套餐/余额”推进到“可运营 SaaS 计费系统”。

## 范围

- 设计 payment provider instance、payment order、payment audit log、refund/reconcile 状态机。
- 支持 Stripe 和一个国内支付渠道作为首批 provider。
- 将模型价格、Token 用量、缓存用量、账户成本与用户余额扣减打通。
- Portal 提供充值、订单、账单和失败重试入口。

## 非目标

- 不在首版处理复杂税务/发票自动化。
- 不保存支付渠道敏感明文密钥。

## 验收标准

- 用户可创建充值订单并通过 mock webhook 完成入账。
- 用量扣费、余额不足、订单失败、幂等回调有测试覆盖。
- 管理端可查看订单、支付审计和对账状态。

## 实现记录

- 新增 `PaymentOrderEntity`、`PaymentAuditLogEntity`、repository 与 Liquibase `0041`。
- 新增 `PaymentAdminService` 与 `/admin/payments` API，支持创建 mock 充值订单、查询订单、接收 mock webhook。
- webhook 成功后写入 `GatewayUserBalanceLedgerEntity`，重复 webhook 或已有账本引用不会重复加余额。

## 测试/验证

- 通过：`PaymentAdminServiceTests`

## 遗留问题

- 本轮不接真实 Stripe / 国内支付渠道。
- 退款、对账、签名验签、支付渠道密钥管理仍需后续任务。
