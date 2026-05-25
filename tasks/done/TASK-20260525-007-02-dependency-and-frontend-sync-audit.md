# TASK-20260525-007-02 同步依赖与前端同步阻塞排查

## 类型

子任务 / task spec

## 背景

除后端显式阻塞调用外，同步依赖和前端主线程同步阻塞也可能造成“项目不是全异步”的体感问题。本子任务补充依赖层和前端明显同步风险。

## 目标

- 识别 `build.gradle` 中影响异步边界的同步组件。
- 扫描 `web/src` 中明显长时间同步阻塞或同步 XHR 风险。
- 区分合理同步依赖、低频管理路径和需后续治理的架构风险。

## 非目标

- 不重写前端请求层。
- 不迁移数据库或 Redis starter。

## 上游来源

- `docs/requirements/REQ-20260525-007-async-boundary-sync-code-audit.md`
- `TASK-20260525-007-async-boundary-sync-code-audit-parent.md`

## 输入

- `build.gradle`
- `web/src`
- `src/test/java`

## 输出

- 报告中的同步依赖和前端风险说明。
- 后续修复 backlog 任务。

## 影响范围

- 项目依赖栈。
- Admin/Portal 前端交互。
- 测试验证口径。

## 依赖

- 静态扫描命令。

## 风险

- 依赖层同步不一定等于立即缺陷，但会影响异步架构承诺。
- 前端局部同步计算需结合实际数据规模判断。

## 验收标准

- 同步依赖有明确架构风险说明。
- 前端无明显同步 XHR 或长时间阻塞时，需要给出扫描证据。

## 测试边界

- 不运行前端测试。
- 以静态扫描为主。

## 当前状态

Done

## 实现结果

- 已确认 `build.gradle` 同时使用 WebFlux、JPA、同步 Redis、Mail 和 Liquibase，项目不是端到端 reactive 依赖栈。
- 已确认前端未发现同步 XHR、`async: false` 或 `Atomics.wait` 等明显长阻塞模式。
- 已确认 Portal OAuth、管理端 smoke、运维拨测、Webhook dispatch 属于中风险或低频同步路径。

## 验证情况

- 已执行依赖、Redis/JPA/file I/O 和前端同步阻塞静态扫描。
- 本轮未运行前端测试。
