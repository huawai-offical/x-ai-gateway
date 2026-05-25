# TASK-20260525-007 异步边界同步代码排查父任务

## 类型

父任务 / task spec

## 背景

用户反馈项目整体应为异步，但怀疑存在同步代码。本任务承接本地需求文档，负责完成项目级异步边界排查、风险归类和后续修复拆分。

## 目标

- 建立异步边界排查范围和证据口径。
- 汇总后端热路径、依赖层、测试和前端同步风险。
- 产出本地分析报告。
- 为后续修复创建可执行子任务。

## 非目标

- 不在本任务内直接替换 JPA、Redis 或 HTTP client 实现。
- 不做性能压测。
- 不处理与异步边界无关的 UI 或接口问题。

## 上游来源

- `docs/requirements/REQ-20260525-007-async-boundary-sync-code-audit.md`

## 子任务

- `TASK-20260525-007-01-backend-runtime-blocking-audit.md`
- `TASK-20260525-007-02-dependency-and-frontend-sync-audit.md`

## 输入

- `src/main/java`
- `src/test/java`
- `web/src`
- `build.gradle`

## 输出

- `docs/reports/REP-20260525-007-async-boundary-sync-code-audit.md`
- 后续修复 backlog 任务。

## 影响范围

- 网关请求执行链路。
- 管理端 API。
- 持久化和外部资源访问。
- 前端交互线程。

## 依赖

- 现有代码静态扫描结果。
- 当前仓库本地任务体系。

## 风险

- 静态扫描可能把测试阻塞写法误判为生产问题。
- 同步依赖修复可能需要跨模块重构。

## 验收标准

- 报告覆盖高风险/中风险同步点。
- 低风险或非生产路径同步点有明确排除说明。
- 后续修复事项不只停留在总结里。

## 测试边界

- 本轮验证方式为静态扫描命令和人工复核。
- 不运行全量自动化测试。

## 关联文档

- `docs/requirements/REQ-20260525-007-async-boundary-sync-code-audit.md`

## 当前状态

Done

## 进度记录

- 2026-05-25：创建父任务，开始异步边界同步代码排查。
- 2026-05-25：完成静态扫描与关键调用链复核，输出 `docs/reports/REP-20260525-007-async-boundary-sync-code-audit.md`。
- 2026-05-25：确认项目当前为 WebFlux 入口与同步服务核心混合形态，后续修复已拆入 backlog。

## 实现结果

- 已确认公开 Chat/Responses 非流式热路径、Resource Executor 契约、同步 JPA/Redis、文件 I/O、Portal OAuth 和管理端 smoke/运维路径中的同步风险。
- 已新增后续修复任务：
  - `tasks/backlog/TASK-20260525-007-03-chat-runtime-reactive-boundary.md`
  - `tasks/backlog/TASK-20260525-007-04-resource-executor-reactive-boundary.md`
  - `tasks/backlog/TASK-20260525-007-05-blocking-infra-isolation-guardrails.md`

## 验证情况

- 已执行报告中记录的 `rg` 静态扫描命令。
- 已人工复核核心 controller、service、runtime、executor 调用链。
- 本轮只修改文档和任务文件，未运行自动化测试。
