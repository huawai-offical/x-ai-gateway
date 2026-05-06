# REQ-20260506-009 支付生产闭环完善

状态：Done  
关联任务：[TASK-20260506-010 支付生产闭环完善](../../tasks/done/TASK-20260506-010-production-payment-closure.md)  
来源报告：[REP-20260506 三个参考项目功能完成度复核](../reports/REP-20260506-reference-feature-completeness-review.md)

## 背景

当前支付能力已经具备订单、余额流水、provider webhook 签名验签、幂等入账与审计基础，但仍停留在“可接收支付通知”的阶段。对照参考项目中成熟的订阅、充值与支付运营能力，还需要补齐主动下单、收银入口、退款、争议、主动对账、多 provider 生产配置边界和本地 smoke 文档。

## 目标

- 支持 Portal 用户创建充值订单，并拿到可跳转或可展示的 checkout 信息。
- 支持 Admin 查询 provider 能力、拉取订单 checkout 信息、执行退款、争议标记和主动对账。
- 扩展支付订单状态机，覆盖 `PENDING`、`PAID`、`FAILED`、`PARTIAL_REFUNDED`、`REFUNDED`、`DISPUTED`。
- 覆盖 Stripe、EasyPay、支付宝官方、微信官方和通用 HMAC provider 的生产配置形态，不在仓库提交真实商户密钥。
- 将配置、安全边界、验收命令和 smoke 步骤沉淀到本地文档。

## 范围

- 后端支付订单实体、Liquibase 变更、Admin API、Portal API 和核心业务服务。
- Portal 前台充值表单、订单列表和 checkout 展示。
- 单元测试覆盖下单、checkout、退款、争议、对账、webhook 幂等和 provider 签名分支。
- 支付 provider 文档更新。

## 非目标

- 不在本轮提交真实商户密钥、真实证书或生产商户号。
- 不在本轮绕过 provider 官方 SDK 或官方 API 文档做不可靠的远程扣款调用。
- 不实现复杂订阅套餐计费、发票税务和跨币种结算。

## 设计

### Provider checkout 规划

支付下单阶段由统一 checkout 规划器生成 provider 侧收银信息：

- `stripe`：生成 Stripe Checkout Session 形态的 checkout URL 与 provider payload。
- `easypay`：生成 EasyPay 页面支付形态的 checkout URL 与 provider payload。
- `alipay`：生成支付宝官方电脑网站支付形态的 checkout URL 与 provider payload。
- `wechat`：生成微信官方 Native 支付二维码形态的 checkout URL 与 provider payload。
- `generic_hmac`、`mock`：保留本地和测试 provider 的 checkout 形态。

provider 真实密钥通过部署环境配置注入，仓库只保存字段结构、校验规则和 smoke 步骤。

### 订单状态机

- `PENDING`：订单创建后等待 provider 回调或主动对账。
- `PAID`：provider 成功通知后入账。
- `FAILED`：provider 明确失败、金额不匹配或签名不通过。
- `PARTIAL_REFUNDED`：部分退款完成，按退款比例冲减 token。
- `REFUNDED`：全额退款完成，冲减本订单全部 token。
- `DISPUTED`：支付争议或拒付，需要运营介入。

### Portal 自助充值

Portal 用户可以创建充值订单，页面展示 provider、金额、token、状态、checkout URL 和最近订单。订单创建后通过 Portal API 返回 checkout 信息，用户可跳转到 provider 收银页或复制链接。

### Admin 运营闭环

Admin API 提供：

- provider 能力列表。
- 订单创建和 checkout 查询。
- provider webhook 接收与验签。
- 退款接口。
- 争议标记接口。
- 指定时间窗口的主动对账报告。

## 风险

- 真实 provider 的 API 字段和签名规则会随商户类型变化，需要部署前按商户账号补 smoke。
- 部分退款冲减 token 可能与后续套餐、赠送额度叠加，需要审计留痕。
- 对账任务本轮以主动接口和报告为主，后续可扩展为定时任务。

## 验收标准

- 后端测试覆盖 checkout 生成、Portal 下单、退款、争议、对账和原有 webhook 幂等。
- Portal 页面可以创建充值订单并展示 checkout 信息。
- Admin API 可以完成订单查询、checkout 查询、退款、争议和对账。
- `docs/payment-provider-webhooks.md` 记录 provider 配置、安全边界和本地 smoke 步骤。
- 任务文件回写实现结果、验证情况、遗留问题和后续建议，并移动到 `tasks/done/`。

## 实现结果

- 已新增支付 checkout 规划器和订单字段迁移 `db.changelog-0045-payment-production-closure.yaml`。
- 已补齐 Admin 支付运营 API：provider 能力、checkout、退款、争议和主动对账。
- 已补齐 Portal 自助充值入口：`POST /portal/orders`、前台充值表单、最近订单和 checkout 链接。
- 已扩展订单状态机：`PENDING`、`PAID`、`FAILED`、`PARTIAL_REFUNDED`、`REFUNDED`、`DISPUTED`。
- 已更新 `docs/payment-provider-webhooks.md` 作为当前支付 provider 事实源。

## 验收结果

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.PaymentAdminServiceTests"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.PaymentAdminServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests"
cd web
bun run typecheck
```

结论：本地实现与类型检查通过。真实商户远程扣款、远程退款和主动查询仍需部署前按商户账号补 smoke。
