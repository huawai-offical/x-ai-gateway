# TASK-20260501-015 路由策略配置 UI、Retry/Fallback 与熔断模型

状态：Done  
优先级：High  
来源：本地拆分  
关联任务：[TASK-20260501-008](../done/TASK-20260501-008-routing-policy-2.md)  
关联设计：[REQ-20260501-003](../../docs/requirements/REQ-20260501-003-second-priority-task-closure-design.md)

## 背景

路由策略 2.0 已完成候选评分解释性，但 retry、fallback、circuit breaker、限流和可视化配置仍需要模型化与前端闭环。

## 本轮目标

先让后端 route guard policy 能承载 retry / fallback / circuit breaker / rate limit 配置，并提供策略摘要 API 供前端读取。

## 本轮范围

- 扩展 route guard policy request/response/entity。
- 增加 routing policy summary API。
- 不改变现有路由选择行为。

## 非目标

- 不在本轮实现完整前端页面。
- 不在本轮改变候选排序基础行为。

## 验收标准

- 策略可通过 Admin API 创建、更新、启停并携带配置字段。
- summary API 可展示启用数量、配置数量和策略分布。
- 单元测试覆盖保存与摘要。

## 实现记录

- `RouteGuardPolicyEntity`、`RouteGuardPolicyRequest`、`RouteGuardPolicyResponse` 增加 `retryPolicy`、`fallbackPolicy`、`circuitBreakerPolicy`、`rateLimitPolicy`。
- 新增 Liquibase `db.changelog-0040-route-policy-config.yaml`，为 `route_guard_policy` 添加 4 个 TEXT 字段。
- `GovernanceAdminService.saveRouteGuard` 支持配置字段保存，并在 audit detail 中记录配置是否存在。
- 新增 `RoutingPolicySummaryResponse` 与 `/admin/ops/policies/routing-summary`，返回策略总数、启用数、四类配置覆盖数和策略列表。

## 测试/验证

- 通过：`GovernanceAdminServiceTests`，覆盖策略配置保存、summary 覆盖统计和 audit detail。
- 通过：`GovernanceAdminControllerTests`，覆盖新增 response 字段序列化。

## 遗留问题

- 本轮只完成后端模型和摘要 API，未实现完整前端配置 UI。
- 配置字段尚未驱动运行时 retry、fallback、circuit breaker、rate limit 行为。
