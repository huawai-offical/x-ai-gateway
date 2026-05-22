# TASK-20260522-010 Observability Redis 超时退避与同步兜底

状态：Done  
优先级：High  
父任务：本任务直接承接 Redis 超时运行故障修复  
上游来源：[REQ-20260522-010 Observability Redis 超时退避与同步兜底](../../docs/requirements/REQ-20260522-010-observability-redis-timeout-backoff.md)

## 背景

运行日志显示 `GatewayObservabilityAsyncPersistenceService.flushBatch()` 从 Redis 热路径队列读取数据时发生 `Redis command timed out after 5 second(s)`。当前异步 observability 队列每秒 flush 一次，Redis 间歇性不可用时会反复等待完整命令超时，并可能让请求热路径的 `enqueue` 也先等待超时再回退同步写库。

## 目标

- 为 observability Redis 队列增加故障退避。
- 退避期间让请求热路径直接走同步写库兜底。
- 退避期间让后台 flush 跳过 Redis 读取。
- Redis 恢复后自动回到异步批量落库。
- 用定向单测覆盖 Redis 读写超时和恢复。

## 非目标

- 不调整 Redis 服务端配置。
- 不清理 Redis 队列数据。
- 不重构整个 observability 落库模型。
- 不处理无关前端、账号分组、Model Policy 或 baseline 变更。

## 输入

- 用户提供的运行日志：`从 Redis 读取 observability 热路径队列失败`、`RedisCommandTimeoutException: Command timed out after 5 second(s)`。
- 当前代码：`GatewayObservabilityAsyncPersistenceService.flushBatch()` 与 `enqueue()`。
- 当前配置：`spring.data.redis.timeout=5s`、`gateway.observability.async.flush-interval=PT1S`、`batch-size=200`。

## 输出

- 可配置 `redis-failure-backoff`。
- Redis 故障窗口内的短路逻辑。
- 定向单测。
- 本地需求与任务回写。

## 影响范围

- `GatewayObservabilityAsyncPersistenceService`
- `GatewayProperties.Observability.Async`
- `application.yaml`
- `GatewayObservabilityAsyncPersistenceServiceTests`
- 本任务和需求文档

## 依赖

- 既有调用方在 `enqueue...()` 返回 `false` 时同步写库。
- 既有 Redis 队列 flush 和 requeue 机制。

## 风险

- Redis 不可用期间同步写库压力增加。
- 退避期间 Redis 队列内旧数据不会立即落库，观测数据存在短延迟。
- 若 PostgreSQL 同时压力过高，需要另行拆分持久化缓冲或限流任务。

## 验收标准

- Redis 读超时后进入退避，下一次 `flushBatch()` 不访问 Redis。
- Redis 写超时后进入退避，下一次 `enqueue...()` 不访问 Redis 并返回 `false`。
- 退避到期且 Redis 可用后，`enqueue...()` 和 `flushBatch()` 可恢复正常队列行为。
- 定向测试通过。

## 测试边界

- 运行 `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityAsyncPersistenceServiceTests"`。
- 不运行全量前端测试；本任务不涉及前端。

## 关联文档

- [REQ-20260522-010](../../docs/requirements/REQ-20260522-010-observability-redis-timeout-backoff.md)

## 关联任务

- 当前任务：`TASK-20260522-010`
- 历史相关：[TASK-20260521-011-05 Redis 热数据增强与 PostgreSQL 回写边界](../done/TASK-20260521-011-05-redis-hot-data-writeback.md)

## 当前状态

- Done：Redis 超时退避与同步兜底已实现，定向测试已通过。

## 实施结果

- 新增 `gateway.observability.async.redis-failure-backoff`，默认 `PT30S`。
- Redis 队列读、写、回写失败后进入本实例退避窗口。
- 退避期间 `flushBatch()` 不访问 Redis，`enqueue...()` 返回 `false` 并触发既有同步写库兜底。
- Redis 可用且退避到期后自动恢复异步队列路径。
- 最小修复了工作区既有编译阻断：`DistributedKeyAdminService` 的协议簇字段名，以及 `CatalogCandidateView` 的 `vendorCode` 构造器漂移。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityAsyncPersistenceServiceTests"`：通过。

## 遗留问题

- Redis timeout 的根因仍需结合生产 Redis/网络指标排查；本任务已避免应用热路径持续被 Redis 超时拖住。
- 当前工作区仍有大量其他未提交改动，本任务未对无关业务范围做清理。
