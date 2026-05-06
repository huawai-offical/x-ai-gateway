# 监控与账务 Rollup

## 管理端入口

```text
GET /admin/observability/billing-rollup
GET /admin/observability/billing-rollup.csv
```

查询参数：

- `period`：`day`、`week`、`month`
- `distributedKeyId`：可选，限制到指定 Distributed Key
- `providerType`：可选，限制到指定 provider
- `from` / `to`：可选，ISO-8601 时间窗口

## 聚合内容

Rollup 会即时聚合以下明细：

- `request_log`：请求数、成功数、失败数、失败率、平均耗时、渠道健康。
- `usage_record`：prompt、completion、reasoning、total、cache hit/write、saved input tokens。
- `payment_order`：已支付订单数、金额、购买 token credits。
- `gateway_user_balance_ledger`：余额流水 credit、debit、net 与窗口内最后余额。

## 维度

响应中包含：

- `buckets`：按 day/week/month 时间桶聚合。
- `byProvider`：按 provider 聚合。
- `byModel`：按 model group 聚合。
- `byDistributedKey`：按 Distributed Key 聚合。
- `channelHealth`：按 provider 计算失败率、平均耗时和最近错误。

## CSV 导出

`/billing-rollup.csv` 输出 total、provider、model、distributedKey 四类 section，便于离线审计或导入表格工具。

## 当前取舍

首版采用明细即时聚合，不引入持久化 rollup 表，也不清理历史明细。后续如果长周期数据量增大，应增加日级 rollup 表、定时任务和归档策略。

## 验收覆盖

- `MonitoringBillingRollupServiceTests.shouldAggregateUsageBillingAndChannelHealth`
