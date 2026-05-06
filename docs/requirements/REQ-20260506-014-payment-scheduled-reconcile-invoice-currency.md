# REQ-20260506-014 支付定时对账、订阅发票与跨币种结算

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-018 支付定时对账、订阅发票与跨币种结算](../../tasks/done/TASK-20260506-018-payment-scheduled-reconcile-invoice-currency.md)

## 背景

支付生产闭环已经具备 checkout、provider webhook、退款、争议和主动对账 API。对照 `sub2api-main` 的支付运营能力，当前还缺定时对账任务、对账审计、发票/税务记录草案、订阅账单关联和跨币种结算快照。

## 目标

- 将手动主动对账扩展为可调度的 reconciliation run。
- 为支付订单补充 invoice/tax/currency settlement metadata，形成可追溯的账务快照。
- 将对账异常写入 audit，后续可接入 ops alert。
- 保持本轮为本地可验证闭环，不接入真实商户密钥。

## 范围

- 后端支付服务、订单实体或 metadata、测试和文档。
- 定时/计划对账 run 的 service 层最小模型。
- invoice number、tax profile、settlement currency、exchange rate snapshot、base amount 等字段。
- 对账 run 与现有 `PaymentReconcileReportResponse` 的复用。

## 非目标

- 不做真实税务合规承诺。
- 不接入真实发票服务或真实汇率服务。
- 不提交任何商户密钥。
- 不实现复杂套餐 prorate、企业合同发票和多税区计算。

## 方案

1. 将 `TASK-018` 移入 `in-progress`。
2. 复用现有支付订单 metadata，优先避免大规模 schema 变更；如字段已存在不足，再补最小实体字段。
3. 新增 service 层计划对账入口，记录 run id、窗口、provider、匹配/异常数量和审计事件。
4. 订单创建时从 metadata 中提取 invoice/tax/currency settlement 快照并写入订单或 provider payload。
5. 增加单元测试覆盖定时对账、异常审计、跨币种快照和 invoice metadata。

## 风险

- 发票税务和跨币种真实规则复杂，本轮必须保持“数据模型和审计基础”，不把业务承诺写死。
- 对账任务如果未来接入真实 provider 查询，需要分布式锁和幂等；本轮先在 service 层预留 run id 与审计。
- 当前工作区大量支付相关文件已有未提交改动，改动时必须只增量叠加，不回退既有内容。

## 验收标准

- 可通过 service/API 触发 scheduled reconciliation run，并返回 run id 与窗口结果。
- 对账异常能写入 `PaymentAuditLogEntity`。
- 支付订单或 provider payload 中有 invoice/tax/currency settlement 快照。
- 单元测试覆盖 paid/pending/failed/disputed/refunded 订单对账分类、跨币种快照和发票 metadata。
- 文档和任务回写完成。

## 实现结果

- `PaymentCheckoutPlanner` 在订单创建时从 `metadataJson` 提取并写入 provider payload 快照：
  - `invoice`：发票号、抬头、邮箱。
  - `tax`：税务 profile、税率 bps、是否含税。
  - `settlement`：结算币种、汇率、基础币种、基础金额和结算金额。
  - `billing`：订阅 ID、账期起止时间和账单周期。
- `PaymentAdminService#runScheduledReconcile` 新增计划对账 run：
  - 生成 `pay_recon_...` run id。
  - 复用现有 reconciliation report，返回窗口、provider、订单数量和异常数量。
  - 对 `PENDING`、`FAILED`、`DISPUTED` 等非 `MATCHED` 状态写入 `RECONCILE_ANOMALY` audit。
- `POST /admin/payments/reconcile/scheduled` 新增 Admin 显式触发入口。
- `PaymentReconciliationScheduler` 新增默认关闭的定时任务壳：
  - `gateway.payment.reconciliation.enabled=true` 时启用。
  - `gateway.payment.reconciliation.fixed-delay`、`provider`、`status` 可配置。
- `PaymentProviderCapabilityResponse` 能力列表增加 `scheduled_reconcile`。

## 测试/验证

- 已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.PaymentAdminServiceTests"
```

- 覆盖点：
  - provider payload 中包含 invoice/tax/settlement/billing 快照。
  - scheduled reconciliation run 返回 run id 与窗口结果。
  - `PAID`、`REFUNDED` 匹配为 `MATCHED`。
  - `PENDING`、`FAILED`、`DISPUTED` 被计入异常并写入 `RECONCILE_ANOMALY` audit。

## 遗留问题

- 真实 provider 远程账单查询、汇率服务和发票服务尚未接入；本轮只建立本地可验证的数据快照与审计基础。
- 定时任务当前默认关闭，生产启用前还需要结合部署环境配置分布式锁或单实例保障。
- 复杂订阅 prorate、多税区合规与企业合同发票仍需后续独立任务展开。
