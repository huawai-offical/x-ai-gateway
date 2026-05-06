# Payment Provider Webhooks

关联需求：[REQ-20260506-002 第八批高优先级任务闭环设计](requirements/REQ-20260506-002-eighth-priority-task-closure-design.md)  
生产闭环需求：[REQ-20260506-009 支付生产闭环完善](requirements/REQ-20260506-009-production-payment-closure.md)  
关联任务：[TASK-20260501-021 真实支付渠道、签名验签与对账](../tasks/done/TASK-20260501-021-payment-real-provider-reconcile.md)
当前增强任务：[TASK-20260506-010 支付生产闭环完善](../tasks/done/TASK-20260506-010-production-payment-closure.md)
账务增强任务：[TASK-20260506-018 支付定时对账、订阅发票与跨币种结算](../tasks/done/TASK-20260506-018-payment-scheduled-reconcile-invoice-currency.md)

## 实现范围

- 新增 `PaymentProviderWebhookRequest`，用于承载 provider、payload、签名头和 webhook secret。
- 新增 `POST /admin/payments/webhooks/provider`，进入真实 provider webhook 处理链路。
- `PaymentAdminService#acceptProviderWebhook` 支持：
  - `stripe`：兼容 Stripe `t=...,v1=...` 签名头，按 `timestamp.payload` 计算 HMAC SHA256。
  - `easypay`、`wechat`、`alipay`、`generic_hmac`：兼容 `sha256=<hex>` 或纯 hex 的 HMAC SHA256 签名。
- mock webhook 与真实 provider webhook 共用统一订单状态机、余额入账、审计与幂等逻辑。
- 新增统一 checkout 规划能力，订单创建时会写入 `provider_instance_code`、`checkout_url`、`checkout_method`、`provider_payload_json` 和 `checkout_expires_at`。
- 新增 Admin 支付运营接口：
  - `GET /admin/payments/providers`
  - `GET /admin/payments/orders/{orderNo}/checkout`
  - `POST /admin/payments/orders/{orderNo}/refund`
  - `POST /admin/payments/orders/{orderNo}/dispute`
  - `POST /admin/payments/reconcile`
  - `POST /admin/payments/reconcile/scheduled`
- 新增 Portal 自助充值接口：`POST /portal/orders`，返回用户可见的 checkout 信息。

## Provider checkout 配置

订单 `metadataJson` 支持以下字段：

- `providerInstanceCode` 或 `provider_instance_code`：生产 provider 实例标识，例如 `stripe-prod-cn`。
- `checkoutBaseUrl` 或 `checkout_base_url`：provider 收银入口或本地 smoke 入口。
- `successUrl` 或 `success_url`：支付成功后的跳转地址。
- `cancelUrl` 或 `cancel_url`：用户取消后的跳转地址。
- `notifyUrl` 或 `notify_url`：provider 回调地址。
- `subject`：订单标题。

订单 `metadataJson` 也支持账务快照字段，生成 checkout 时会写入 `provider_payload_json`：

- 发票：`invoiceNo` / `invoice_no`、`invoiceTitle` / `invoice_title`、`invoiceEmail` / `invoice_email`。
- 税务：`taxProfileCode` / `tax_profile_code`、`taxRateBps` / `tax_rate_bps`、`taxInclusive` / `tax_inclusive`。
- 结算：`settlementCurrency` / `settlement_currency`、`exchangeRate` / `exchange_rate`、`baseAmountMinor` / `base_amount_minor`、`settlementAmountMinor` / `settlement_amount_minor`。
- 订阅账单：`subscriptionId` / `subscription_id`、`billingPeriodStart` / `billing_period_start`、`billingPeriodEnd` / `billing_period_end`、`billingCycle` / `billing_cycle`。

默认 provider checkout 形态：

| provider | checkoutMethod | 默认入口 | webhook 签名 |
| --- | --- | --- | --- |
| `stripe` | `stripe_checkout_session` | `https://checkout.stripe.com/c/pay` | Stripe `t/v1` HMAC |
| `easypay` | `easypay_page` | `https://pay.easypay.example/submit` | `sha256` HMAC |
| `alipay` | `alipay_page_pay` | `https://openapi.alipay.com/gateway.do` | `sha256` HMAC |
| `wechat` | `wechat_native_qr` | `weixin://wxpay/bizpayurl` | `sha256` HMAC |
| `generic_hmac` | `generic_hmac_page` | `https://gateway.local/pay/generic_hmac` | `sha256` HMAC |
| `mock` | `mock_page` | `https://gateway.local/pay/mock` | mock payload |

真实商户密钥、证书和 webhook secret 不提交到仓库，应由部署环境或密钥管理系统注入。

## 校验与幂等

- 幂等键统一为 `{provider}:{providerEventId}`。
- 已存在 webhook audit 或已存在 `PAYMENT_ORDER` ledger 时返回幂等响应，不重复入账。
- provider、金额、币种不一致会将订单置为 `FAILED`，并写入对应审计事件。
- provider webhook 成功状态支持 `PAID`、`SUCCEEDED`、`SUCCESS`，失败状态写入 `WEBHOOK_FAILED`。

## 退款、争议与对账

- 退款仅允许 `PAID` 或 `PARTIAL_REFUNDED` 订单执行。
- 部分退款会将订单置为 `PARTIAL_REFUNDED`，全额退款置为 `REFUNDED`。
- 退款按金额比例冲减本订单入账的 token，写入 `PAYMENT_REFUND` 余额流水。
- 争议标记会将订单置为 `DISPUTED`，不自动冲减 token，等待运营处理。
- 主动对账会在指定时间窗口内更新 `reconciled_at` 和 `reconcile_status`：
  - `PAID`、`REFUNDED`、`PARTIAL_REFUNDED` -> `MATCHED`
  - `PENDING` -> `PENDING_PROVIDER_CONFIRMATION`
  - `FAILED` -> `FAILED_PROVIDER_CONFIRMATION`
  - `DISPUTED` -> `DISPUTED_REVIEW_REQUIRED`
- 计划对账通过 `POST /admin/payments/reconcile/scheduled` 或 `PaymentReconciliationScheduler` 触发，返回 `runId`、窗口、订单数、异常数和原始 report。
- 计划对账默认关闭，启用配置：
  - `gateway.payment.reconciliation.enabled=true`
  - `gateway.payment.reconciliation.fixed-delay=PT6H`
  - `gateway.payment.reconciliation.provider=stripe`
  - `gateway.payment.reconciliation.status=PENDING`
- 计划对账会为非 `MATCHED` 订单写入 `RECONCILE_ANOMALY` audit，payload 包含 `runId`、窗口、订单状态和对账状态。

## 验证

已通过目标测试：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.PaymentAdminServiceTests"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.PaymentAdminServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests"
cd web
bun run typecheck
```

覆盖点：

- mock webhook 余额入账幂等。
- Stripe 签名 webhook 成功入账与重复通知幂等。
- Stripe 错误签名拒绝。
- Stripe 金额不一致失败且不入账。
- 通用 HMAC provider webhook 成功入账。
- checkout 生成、provider 能力列表和 checkout 查询。
- 部分退款、余额冲减和争议审计。
- 按 provider 过滤的主动对账报告。
- 计划对账 run、异常统计和 `RECONCILE_ANOMALY` 审计。
- invoice/tax/settlement/billing 快照写入 provider payload。
- Portal 前端充值 API 类型与页面类型检查。

## 后续边界

- 本轮不在仓库保存真实 webhook secret，生产环境应接入加密配置或密钥管理。
- 真实 provider 远程扣款、退款和查询请求需要在部署前使用商户账号补一轮 smoke，并替换默认 checkout base URL。
- 真实 provider 远程账单查询、真实汇率、真实发票服务仍需接入部署环境和商户账号后继续补 smoke。
- 复杂订阅 prorate、多税区税务合规和企业合同发票仍作为后续增强项。
