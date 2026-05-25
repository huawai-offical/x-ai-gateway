# TASK-20260525-007-05 阻塞基础设施隔离与防回退护栏

## 类型

子任务 / task spec

## 背景

项目同时使用 WebFlux、JPA、同步 Redis、同步文件 I/O、JavaMailSender 和部分同步外部 SDK。即使接口契约改为 reactive，如果不建立隔离和防回退规则，阻塞调用仍可能重新进入 event loop。

## 目标

- 对公开请求热路径中的 JPA、Redis、文件 I/O、OAuth profile client 做阻塞隔离策略。
- 梳理哪些同步组件需要迁移、哪些只需放入 elastic worker、哪些属于 admin/maintenance 低频路径。
- 增加静态扫描或架构测试，禁止核心热路径新增 `block()`、`HttpClient.send()`、`Thread.sleep()`。
- 对 Redis `KEYS` 类操作建立替代或 admin-only 边界。

## 非目标

- 不一次性迁移到 R2DBC。
- 不重构所有 admin maintenance/smoke 工具。
- 不新增性能压测平台。

## 上游来源

- `docs/requirements/REQ-20260525-007-async-boundary-sync-code-audit.md`
- `docs/reports/REP-20260525-007-async-boundary-sync-code-audit.md`

## 输入

- `build.gradle`
- gateway/auth/routing/account/observability Redis store。
- JPA repository 调用链。
- `GatewayFileService`
- `GatewayAsyncResourceService`
- Portal OAuth profile clients。

## 输出

- 阻塞组件分级清单。
- 热路径 elastic 隔离或 reactive 替代方案。
- no-block 静态扫描测试或 ArchUnit/自定义测试。

## 影响范围

- 认证、路由、限流、熔断、账号选择。
- 文件上传/下载。
- Portal 社交 OAuth。
- 运维和 smoke 工具边界。

## 依赖

- Chat runtime 和 Resource executor 响应式改造的边界结果。

## 风险

- 事务边界迁移容易引入一致性问题。
- 简单包裹 `boundedElastic` 只能隔离阻塞，不能提升底层 I/O 吞吐。

## 验收标准

- 核心热路径新增阻塞调用能被测试阻断。
- 已知同步组件都有明确分类和隔离方式。
- Redis `KEYS` 不在公开请求热路径使用。

## 测试边界

- 静态扫描测试。
- 关键 gateway service 定向测试。
- 不要求本任务完成真实压测。

## 关联文档

- `docs/reports/REP-20260525-007-async-boundary-sync-code-audit.md`

## 当前状态

Backlog

