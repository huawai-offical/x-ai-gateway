# TASK-20260501-011 监控与账务 rollup：长周期用量聚合、清理、导出、渠道健康日报

状态：Done  
优先级：Medium  
来源：Notion 待创建；Linear 创建失败  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)  
关联需求：[REQ-20260506-004](../../docs/requirements/REQ-20260506-004-tenth-priority-task-closure-design.md)

## 背景

对标 `new-api` / `Sub2API`，`x-ai-gateway` 已有请求日志、traces、incident 等基础观测能力，但长周期账务聚合、数据清理、导出和渠道健康日报仍需增强。

## 目标

让平台具备可长期运行的用量统计、账务核对和健康巡检能力。

## 范围

- 日/周/月维度用量 rollup。
- 用户、API Key、模型、渠道、供应商维度统计。
- 历史明细清理与归档策略。
- CSV/JSON 导出。
- 渠道健康日报和异常摘要。
- 管理端报表与定时任务状态。

## 非目标

- 不在首版引入复杂 BI 系统。
- 不以删除明细为代价破坏账务可追溯性。

## 验收标准

- 可查看长周期用量与费用汇总。
- 明细清理不影响账务汇总准确性。
- 管理员可导出账务与健康报表。
- 渠道异常可进入日报/告警摘要。

## 实现记录

已完成监控与账务 rollup 首版闭环：

- 新增 `GET /admin/observability/billing-rollup`。
- 新增 `GET /admin/observability/billing-rollup.csv`。
- 新增 `MonitoringBillingRollupService`，聚合 request log、usage record、payment order 与 balance ledger。
- 支持 day/week/month 窗口，支持 provider、model、distributedKey 维度和渠道健康摘要。
- CSV 导出覆盖 total、provider、model、distributedKey section。
- 新增文档：[monitoring-billing-rollup](../../docs/monitoring-billing-rollup.md)。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.MonitoringBillingRollupServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests"
```

覆盖 usage token 汇总、支付订单、余额流水、渠道健康和 CSV 导出。

## 遗留问题

此任务此前因 Linear 免费 issue 数量限制未能创建线上 issue，现以本地任务为准。

首版为即时聚合，不清理明细；长周期大数据量场景应继续增加持久化 rollup 表、定时任务、归档和明细清理策略。
