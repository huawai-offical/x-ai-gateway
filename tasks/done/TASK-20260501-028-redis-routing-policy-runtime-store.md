# TASK-20260501-028 Redis Route Policy Runtime Store 与跨实例一致性

状态：Done
优先级：High
来源：TASK-20260501-025 后续拆分
关联任务：[TASK-20260501-025](../done/TASK-20260501-025-distributed-routing-circuit-rate-limit.md)
关联需求：[REQ-20260501-006](../../docs/requirements/REQ-20260501-006-fifth-priority-task-closure-design.md)
关联推进需求：[REQ-20260505-002](../../docs/requirements/REQ-20260505-002-sixth-priority-task-closure-design.md)

## 背景

第五批已完成本地内存版 Route Policy rate limit 与 circuit breaker 执行器，但生产多实例需要共享状态。

## 目标

将运行态 store 抽象为可替换接口，并提供 Redis 实现，保证多实例限流与熔断一致。

## 范围

- 抽象 `RoutingPolicyRuntimeStore` 接口。
- Redis rate window 原子计数与 TTL。
- Redis circuit state 原子更新、冷却、half-open 探测锁。
- Admin reset 指定 policy/target 与全局 reset。
- 并发与故障恢复测试。

## 非目标

- 不改变 Route Guard policy 的配置模型。
- 不引入外部 SaaS 状态存储。

## 风险

- 熔断 half-open 探测需要避免多实例同时放量。
- Redis 不可用时需要明确 fail-open 或 fail-closed 策略。

## 验收标准

- 多实例共享 rate limit 与 circuit breaker 状态。
- Redis TTL、原子更新和 reset 有测试覆盖。
- Redis 不可用策略可配置且有告警。

## 本批推进记录

- 2026-05-05：进入第六批最高优先级任务闭环，目标是先完成 store 抽象、内存实现迁移、Redis 实现入口和定向测试。
- 2026-05-05：完成 `RoutingPolicyRuntimeStore` 抽象、默认内存实现、Redis 实现、half-open 探测锁、全局/定点 reset 与定向测试。

## 实现结果

- `RoutingPolicyRuntimeEnforcementService` 不再直接持有运行态 Map，改为依赖 `RoutingPolicyRuntimeStore`。
- 新增 `InMemoryRoutingPolicyRuntimeStore` 作为默认实现和测试 fallback。
- 新增 `RedisRoutingPolicyRuntimeStore`，支持 rate window TTL、circuit state hash、half-open probe lock、Redis 不可用时本地 fallback。
- `gateway.routing.runtime-store.type` 可配置为 `memory` 或 `redis`，并支持 key prefix 与 fallback 开关。
- Admin reset 支持全局 reset，也支持 `runtimeKey` 或 `policyId + targetRef` 定点 reset。

## 验证情况

- 已通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.routing.RoutingPolicyRuntimeEnforcementServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.RedisRoutingPolicyRuntimeStoreTests" --tests "com.prodigalgal.xaigateway.admin.application.GovernanceAdminServiceTests"`。

## 遗留问题

- 未连接真实共享 Redis 做跨进程 smoke；已拆到 [TASK-20260505-006](../backlog/TASK-20260505-006-redis-oauth-ops-smoke-harness.md)。
