# TASK-20260506-010 支付生产闭环完善

状态：Done  
优先级：High  
来源：[REP-20260506 三个参考项目功能完成度复核](../../docs/reports/REP-20260506-reference-feature-completeness-review.md)
需求文档：[REQ-20260506-009 支付生产闭环完善](../../docs/requirements/REQ-20260506-009-production-payment-closure.md)

## 背景

当前支付模块已完成订单、余额流水、provider webhook 签名验签、幂等入账与审计，但对照 `sub2api-main`，还缺真实下单、退款、争议、主动对账、多服务商实例路由和前台收银体验。

## 目标

- 将支付从“webhook 入账骨架”推进到生产可用的充值闭环。
- 支持 Stripe、EasyPay、支付宝官方、微信官方的 provider instance 配置与下单流程。
- 提供退款、补单/对账、限额与审计能力。

## 范围

- Payment provider instance 持久化与密钥加密策略。
- 真实 create checkout/payment order 适配。
- webhook 验签与订单状态机扩展。
- 退款、争议、主动查询补单、每日对账报告。
- Portal 前台充值入口与 Admin 支付运营页面补齐。

## 非目标

- 不保存任何真实商户密钥到仓库。
- 不接入不稳定或无文档的第三方聚合渠道。

## 验收标准

- 每个 provider 有独立单测覆盖签名、下单、成功回调、失败回调、重复通知。
- Portal 可创建真实支付订单并看到状态流转。
- Admin 可查看订单、审计、退款/补单结果。
- 本地文档记录每个 provider 的生产配置、安全边界和 smoke 步骤。

## 详细设计

- 新增统一 checkout 规划能力，覆盖 `stripe`、`easypay`、`alipay`、`wechat`、`generic_hmac` 和 `mock`，返回 checkout URL、provider payload 和 provider instance code。
- 扩展支付订单字段，记录 checkout、provider instance、退款、争议和对账状态。
- Admin 补齐 provider 列表、checkout 查询、退款、争议和主动对账 API。
- Portal 补齐用户下单 API、前台充值表单、最近订单和 checkout 信息展示。
- 支付文档补齐生产配置、安全边界和 smoke 步骤。

## 进度记录

- 2026-05-06：任务从 backlog 移入 in-progress，补充本地需求与详细设计，开始实现支付闭环。
- 2026-05-06：完成 checkout 规划、支付订单状态字段、Admin 支付运营 API、Portal 自助充值入口、退款/争议/对账状态机和回归测试。

## 实现结果

- 新增 `PaymentCheckoutPlanner`，订单创建时生成 provider checkout URL、checkout method、provider instance code、provider payload 和过期时间。
- `PaymentOrderEntity` 和 Liquibase `0045` 增加 checkout、退款、争议和对账字段。
- `PaymentAdminService` 支持 provider 能力列表、checkout 查询、退款、争议标记和主动对账。
- `PaymentAdminController` 增加支付运营接口，`PortalAuthController` 增加 `POST /portal/orders` 自助充值下单。
- Portal 首页新增充值表单、最近充值订单和 checkout 链接展示。
- `docs/payment-provider-webhooks.md` 已更新 provider 配置、安全边界、退款、争议、对账和 smoke 命令。

## 验证情况

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.PaymentAdminServiceTests"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.PaymentAdminServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests"
cd web
bun run typecheck
```

覆盖点：

- checkout 生成、provider 能力列表、checkout 查询。
- mock、Stripe、通用 HMAC webhook 入账与幂等。
- 金额不一致失败且不入账。
- 部分退款、余额冲减、争议审计。
- provider 过滤主动对账。
- Portal 充值 API 类型与页面类型检查。

## 遗留问题

- 真实 provider 远程扣款、远程退款和主动查询仍需结合生产商户账号做 smoke。
- 定时对账、发票税务、复杂订阅计费和跨币种结算未纳入本轮。

## 后续建议

- 部署前为 Stripe、EasyPay、支付宝、微信各准备独立 smoke 配置。
- 将对账接口扩展为可调度任务，并把对账报告接入运营告警。
