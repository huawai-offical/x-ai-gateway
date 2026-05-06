# Portal 用户自助增强

关联需求：[REQ-20260506-003 第九批任务闭环设计](requirements/REQ-20260506-003-ninth-priority-task-closure-design.md)  
关联任务：[TASK-20260501-005 Portal 用户自助增强](../tasks/done/TASK-20260501-005-portal-self-service.md)

## 实现范围

- 新增 Portal profile、自助摘要、订单列表、usage summary 和渠道状态接口。
- 自助摘要聚合当前用户资料、余额、API Key、订阅、订单、usage 和 provider/channel 状态。
- 订单数据来自 `payment_order`，只返回当前 Portal 用户自己的订单。
- usage 数据按当前用户拥有的 DistributedKey 汇总最近 100 条 usage 记录，并返回最近 20 条明细。
- 渠道状态基于 active `upstream_site_profile` 与 `site_capability_snapshot` 暴露只读健康态。

## 关键接口

- `GET /portal/profile`
- `GET /portal/self-service/summary`
- `GET /portal/orders`
- `GET /portal/usage/summary`
- `GET /portal/channels/status`

## 空态策略

- 未接入支付、usage 或 site repository 的本地测试场景返回空集合。
- 用户无 Key 时 usage summary 返回 0 请求、0 token 和空明细。
- 渠道没有 capability snapshot 时 health state 返回 `UNKNOWN`。

## 验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests"
```

覆盖：

- profile passkey count。
- 当前余额、订单、usage token 汇总、订阅、Key 与渠道健康态聚合。
