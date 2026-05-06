# TASK-20260501-018 路由策略 UI 与 Retry/Fallback 运行时执行

状态：Done  
优先级：High  
来源：TASK-20260501-015 后续拆分  
关联任务：[TASK-20260501-015](TASK-20260501-015-routing-policy-config-ui.md)  
关联需求：[REQ-20260501-005](../../docs/requirements/REQ-20260501-005-fourth-priority-task-closure-design.md)

## 背景

当前 Route Guard 已能保存 retry、fallback、circuit breaker、rate limit 配置，并提供 summary API；下一步需要让这些配置真正驱动 UI 和运行时。

## 目标

完成前端策略配置体验，并将配置接入路由执行链路。

## 范围

- 前端 route policy 配置页与表单校验。
- route preview 展示 retry/fallback/circuit/rate limit 生效摘要。
- 运行时 retry / fallback 执行。
- circuit breaker 与 rate limit 状态统计。

## 非目标

- 不在此任务中重做 cost routing 模型。
- 不在此任务中引入 Redis 级别分布式限流和半开熔断探测。

## 实现结果

- 新增 `RoutingPolicyRuntimeConfigService`，可解析启用中的 `retryPolicy`、`fallbackPolicy`、`circuitBreakerPolicy`、`rateLimitPolicy`。
- 新增 `RoutingPolicyRuntimePlanResponse` 与 `/admin/ops/policies/routing-runtime-plan` API，返回当前 runtime plan、来源策略和解析 warning。
- `GatewayChatExecutionService` 的非流式与流式 fallback 最大尝试次数接入 runtime retry policy。
- Web 管理端 Route Guard 表单新增四类策略 JSON 字段，Route Guard 卡片展示 Retry/Fallback/Circuit/Rate Limit 配置状态。

## 测试/验证情况

- `GovernanceAdminServiceTests`
- `GovernanceAdminControllerTests`
- `GatewayChatExecutionServiceTests`
- `bun run test -- src/features/ops/governance-page.test.tsx`
- `bun run typecheck`

## 遗留问题

- circuit breaker 与 rate limit 本轮只完成配置解析、可观测 runtime plan 和后续接入点，分布式执行与半开探测留给后续任务。

## 后续建议

- 继续推进 [TASK-20260501-025 分布式 Route Policy 熔断/限流执行器](../backlog/TASK-20260501-025-distributed-routing-circuit-rate-limit.md)。
