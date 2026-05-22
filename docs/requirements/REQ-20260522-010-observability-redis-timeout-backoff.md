# REQ-20260522-010 Observability Redis 超时退避与同步兜底

状态：Done  
创建日期：2026-05-22  
关联任务：
- [TASK-20260522-010 Observability Redis 超时退避与同步兜底](../../tasks/done/TASK-20260522-010-observability-redis-timeout-backoff.md)

## 背景

运行一段时间后，后台调度线程 `xag-scheduler-1` 会在 `GatewayObservabilityAsyncPersistenceService.flushBatch()` 中记录 `Redis command timed out after 5 second(s)`。当前实现每秒尝试从 Redis list 批量 `leftPop` observability 热路径队列；Redis 网络抖动、实例压力、连接阻塞或远端 Redis 不可达时，调度线程会等待完整 Redis command timeout。

同类风险也存在于请求热路径：`enqueue...()` 先尝试 `rightPush` Redis 队列，失败后才回退同步写库。如果 Redis 处于间歇性超时状态，每条请求都可能先等待 5 秒，再进入同步兜底。

## 目标

- Redis 读取或写入 observability 热路径队列失败后，进入短暂退避窗口。
- 退避窗口内不再访问 Redis 队列，`enqueue...()` 直接返回 `false`，由既有调用方同步落库兜底。
- 调度 `flushBatch()` 在退避窗口内跳过 Redis 读取，避免每轮都等待 Lettuce timeout。
- 退避窗口到期后自动重试 Redis；重试成功后恢复异步队列路径。
- 保留已进入 Redis 的历史队列数据，Redis 恢复后继续批量落库。

## 非目标

- 不修改 Redis 服务端部署、网络、密码、database 或连接池拓扑。
- 不清空、迁移或丢弃已有 `xag:observability:hot-path` 队列数据。
- 不改变 request log、usage、route decision、cache hit、credential/account runtime metrics 的字段语义。
- 不引入外部任务系统或线上 SaaS 流程。

## 方案

在 `GatewayObservabilityAsyncPersistenceService` 内增加 Redis 故障冷却状态：

- 新增 `gateway.observability.async.redis-failure-backoff` 配置，默认 `PT30S`。
- `leftPop`、`rightPush`、`leftPushAll` 捕获 Redis 运行时异常后，记录退避截止时间。
- `flushBatch()` 在退避期间返回 `0`，不触发 Redis 命令。
- `enqueue()` 在退避期间返回 `false`，沿用既有同步写库兜底。
- Redis 重试成功后清理退避状态，恢复异步队列。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/observability/GatewayObservabilityAsyncPersistenceService.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/config/GatewayProperties.java`
- `src/main/resources/application.yaml`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/observability/GatewayObservabilityAsyncPersistenceServiceTests.java`

## 风险

- 退避窗口内会增加同步写库比例，PostgreSQL 压力会高于纯 Redis 队列模式。
- Redis 中已排队但尚未 flush 的历史事件会等 Redis 恢复后再落库，期间观测查询可能存在延迟。
- 如果 Redis 长时间不可用，应进一步检查 Redis 实例、网络链路和 Lettuce 连接池配置。

## 验收标准

- Redis `leftPop` 超时时，`flushBatch()` 不抛出异常，后续退避期内不继续调用 Redis。
- Redis `rightPush` 超时时，`enqueue...()` 返回 `false`，调用方可以同步写库兜底。
- 退避窗口到期后，Redis 可用时队列路径恢复。
- 新增/更新定向单测覆盖读超时退避、写超时退避和恢复路径。

## 实施记录

- `GatewayObservabilityAsyncPersistenceService` 增加 Redis 故障退避状态，`leftPop`、`rightPush`、`leftPushAll` 失败后写入退避截止时间。
- `flushBatch()` 在退避窗口内直接返回 `0`，避免调度线程每轮等待 Redis command timeout。
- `enqueue()` 在退避窗口内直接返回 `false`，由 `GatewayRequestLifecycleService` 与 `GatewayObservabilityService` 既有同步落库兜底承接 request log、usage、route decision、cache hit 和 runtime metrics。
- `GatewayProperties.Observability.Async` 新增 `redisFailureBackoff`，`application.yaml` 暴露 `GATEWAY_OBSERVABILITY_ASYNC_REDIS_FAILURE_BACKOFF`，默认 `PT30S`。
- 为了恢复本轮测试编译，最小修正了当前工作区已有的协议簇迁移残留：`DistributedKeyAdminService` 改用 `allowedProtocolSuites`，`CatalogCandidateView` 构造器和 3 个调用点补齐 `vendorCode` 签名。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityAsyncPersistenceServiceTests"`：通过。
- 覆盖场景：Redis 读取超时后 flush 退避、Redis 写入超时后 enqueue 退避、退避到期后恢复队列写入、原有批量 flush/requeue/runtime metrics 行为。

## 后续建议

- 如果生产仍持续出现 Redis timeout，应继续检查 Redis 实例 CPU/内存、慢查询、网络链路、Lettuce 连接池和 `spring.data.redis.timeout`。本轮修复的重点是保护应用热路径和调度线程，不替代 Redis 基础设施排障。
- 退避期间同步写库比例会上升，后续可以给 observability 同步兜底增加 metrics 或系统事件，方便判断 Redis 故障对 PostgreSQL 写入压力的影响。
