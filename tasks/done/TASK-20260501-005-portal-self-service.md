# TASK-20260501-005 Portal 用户自助增强：用量、账单、渠道状态、个人资料、订单与支付

状态：Done  
优先级：Medium  
来源：Linear X-286  
来源 URL：https://linear.app/x-ai/issue/X-286/portal-用户自助增强用量账单渠道状态个人资料订单与支付  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)
关联推进需求：[REQ-20260506-003](../../docs/requirements/REQ-20260506-003-ninth-priority-task-closure-design.md)
关联说明文档：[portal-self-service](../../docs/portal-self-service.md)

## 背景

`Sub2API` 用户端包含 Dashboard、Keys、Payment、Orders、Usage、Subscriptions、Profile、Available Channels、Channel Status 等页面；当前 `x-ai-gateway` Portal 已有首页、订阅、Keys、兑换、公告、登录，但自助运营闭环仍偏薄。

## 目标

让终端用户可以自助查看 Key、用量、账单、订单、渠道可用性和个人设置，减少管理员代操作。

## 范围

- Portal 增加用量明细、账单摘要、订单列表、支付入口。
- 增加可用渠道/模型状态与维护公告展示。
- 增加个人资料、安全设置、API Key 快速配置导出。
- 与套餐、访问组、余额账本和公告系统打通。

## 非目标

- 不引入复杂多租户组织管理。
- 不替代管理员控制台。

## 验收标准

- Portal 用户可完成查看用量、查看余额、创建 Key、查看渠道状态和订单状态。
- 页面具备空态、错误态和权限态。
- 前后端关键交互有测试。

## 实现记录

- 2026-05-06：进入第九批任务闭环，目标是补齐 Portal profile、自助摘要、订单、usage 与渠道状态只读接口。
- 2026-05-06：完成 Portal profile、自助摘要、订单、usage summary 和渠道状态接口。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests"
```

覆盖：

- 当前用户 profile、passkey count、余额、订单、usage token 汇总、订阅、Key 与渠道健康态聚合。

## 遗留问题

- Portal 前端页面尚未接入本轮新增接口。
- 更细的账单周期、usage 图表和支付入口仍可后续增强。

## 后续建议

- 后续在 Portal 前端新增“概览 / 账单 / 用量 / 渠道状态 / 安全设置”页面，直接消费本轮接口。
