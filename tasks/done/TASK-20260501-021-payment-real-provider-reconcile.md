# TASK-20260501-021 真实支付渠道、签名验签与对账

状态：Done  
优先级：High  
来源：TASK-20260501-004 后续拆分  
关联任务：[TASK-20260501-004](../done/TASK-20260501-004-billing-payment-loop.md)
关联推进需求：[REQ-20260506-002](../../docs/requirements/REQ-20260506-002-eighth-priority-task-closure-design.md)
关联说明文档：[payment-provider-webhooks](../../docs/payment-provider-webhooks.md)

## 背景

当前 Payment 已有 mock 订单、mock webhook 和余额入账，尚未接真实支付渠道。

## 目标

完成真实支付 provider、签名验签、退款和对账状态机。

## 范围

- Stripe provider。
- 一个国内支付 provider。
- webhook 签名验签、重放防护。
- refund / reconcile / dispute 状态。
- 支付渠道密钥加密存储。

## 验收标准

- mock 与真实 provider 共用订单状态机。
- webhook 幂等、签名错误、金额不一致、重复通知均有测试。

## 本批推进记录

- 2026-05-06：进入第八批高优先级任务闭环，目标是补齐 Stripe 签名 webhook、通用 HMAC provider webhook、幂等与金额校验。
- 2026-05-06：完成 provider webhook 请求模型、admin endpoint、Stripe 签名验签、通用 HMAC 验签、统一幂等与金额/币种/provider 校验。

## 实现结果

- 新增 `PaymentProviderWebhookRequest` 与 `POST /admin/payments/webhooks/provider`。
- `PaymentAdminService#acceptProviderWebhook` 支持 `stripe`、`wechat`、`alipay`、`generic_hmac`。
- Stripe 使用 `t=...,v1=...` 签名头，按 `timestamp.payload` 计算 HMAC SHA256。
- 国内支付 provider 基础适配使用 `sha256=<hex>` 或纯 hex HMAC SHA256。
- mock webhook 与 provider webhook 共用订单状态机、余额 ledger、审计日志和幂等键。

## 测试/验证情况

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.PaymentAdminServiceTests"
```

覆盖：

- mock webhook 幂等入账。
- Stripe 签名 webhook 成功入账与重复通知幂等。
- Stripe 错误签名拒绝。
- Stripe 金额不一致失败且不入账。
- 通用 HMAC provider webhook 成功入账。

## 遗留问题

- 退款、争议、渠道主动对账接口、生产密钥加密配置仍未在本轮实现。

## 后续建议

- 后续把 provider secret 迁入加密配置中心，并为 refund/reconcile/dispute 单独拆分任务与状态机测试。
