# TASK-20260501-025 分布式 Route Policy 熔断/限流执行器

状态：Done  
优先级：High  
来源：TASK-20260501-018 后续拆分  
关联任务：[TASK-20260501-018](../done/TASK-20260501-018-routing-policy-ui-runtime.md)  
关联需求：[REQ-20260501-006](../../docs/requirements/REQ-20260501-006-fifth-priority-task-closure-design.md)

## 背景

当前 route policy runtime plan 已可解析 retry/fallback/circuit/rate limit 配置，并可限制 fallback 尝试次数，但 circuit breaker 与 rate limit 还没有分布式运行时执行器。

## 目标

将 circuit breaker 与 rate limit 从可观测配置推进到执行器和状态 API。

## 范围

- Redis 或等价本地 store 的 rate limit 计数。
- 熔断 closed/open/half-open 状态机。
- provider/site/credential/account/proxy 维度 runtime key。
- 管理端状态观测与手动恢复。

## 非目标

- 本轮不引入 Redis。
- 本轮不实现跨实例状态复制。

## 详细设计

- 新增 `RoutingPolicyRuntimeEnforcementService`：
  - 读取启用中的 Route Guard policy。
  - 对候选 provider/site/credential/proxy 匹配 rate limit 与 circuit breaker。
  - 使用本地内存 store 实现分钟窗口计数和熔断状态机。
- `GatewayRouteSelectionService` 在候选评估中调用 `evaluateCandidate`。
- `GatewayChatExecutionService` 在成功/失败后调用 `recordSuccess` / `recordFailure`。
- Admin API 增加 runtime state 查询与 reset。

## 风险

- 本地 store 无法跨实例共享，生产环境必须替换为 Redis 或数据库/缓存后端。
- 熔断状态的失败来源需要避免把不可重试参数错误计入 provider 故障。

## 验收标准

- rate limit 超限时可拒绝候选路由。
- 熔断失败计数达到阈值后进入 OPEN，并在冷却后进入 HALF_OPEN。
- 请求成功/失败会回写执行器状态。
- Admin API 与治理页可查看 runtime state 并手动 reset。

## 实现结果

- 新增 `RoutingPolicyRuntimeEnforcementService`，读取 Route Guard policy 并执行 credential/provider/site/proxy 维度的 rate limit 与 circuit breaker。
- `GatewayRouteSelectionService` 已在候选评估中调用运行时执行器，超限或熔断时过滤候选。
- `GatewayChatExecutionService` 已在成功/失败后回写 `recordSuccess` / `recordFailure`。
- `GovernanceAdminController` 与 `GovernanceAdminService` 已暴露 runtime state 查询和 reset。
- 治理页 `Route Guards` tab 已展示 runtime states、open/rate window 统计与 reset 按钮。

## 测试/验证情况

- 通过 `RoutingPolicyRuntimeEnforcementServiceTests` 覆盖 rate limit、circuit open、success close 与 reset。
- 通过 `GatewayRouteSelectionServiceTests`、`GatewayChatExecutionServiceTests`、`GovernanceAdminServiceTests` 回归路由、执行和管理服务。
- 通过 `governance-page.test.tsx` 覆盖前端 runtime state 展示与 reset。
- 通过 `bun run typecheck`。

## 遗留问题

- 当前运行态 store 仍为本地内存，暂不支持跨实例共享。
- ACCOUNT 维度需要在候选上下文补齐 account identity 后再纳入执行。

## 后续建议

- 新增 `TASK-20260501-028` 推进 Redis Route Policy Runtime Store 与跨实例一致性。
