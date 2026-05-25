# TASK-20260525-007-01 后端运行时阻塞调用排查

## 类型

子任务 / task spec

## 背景

WebFlux 请求链路中如果出现 `block()`、同步 HTTP client、同步 repository 或线程休眠，会破坏异步边界。本子任务专注后端生产代码运行时风险。

## 目标

- 扫描 `src/main/java` 中显式阻塞调用。
- 复核 controller/service/runtime/executor 热路径中的同步 repository 和外部 I/O。
- 标记需要后续修复的具体文件和方法。

## 非目标

- 不改造具体实现。
- 不分析测试代码中的阻塞等待。

## 上游来源

- `docs/requirements/REQ-20260525-007-async-boundary-sync-code-audit.md`
- `TASK-20260525-007-async-boundary-sync-code-audit-parent.md`

## 输入

- `src/main/java`
- `build.gradle`

## 输出

- 报告中的后端运行时同步风险清单。
- 后续修复 backlog 任务。

## 影响范围

- 网关协议入口。
- 管理端 API。
- 持久化、Redis、HTTP 上游调用。

## 依赖

- 静态扫描命令。
- 人工代码复核。

## 风险

- 同步 JPA 可能是跨项目架构问题，需要分阶段处理。
- 某些同步调用可能只在启动、低频管理或测试 fixture 中触发。

## 验收标准

- 每个高/中风险点都有路径、证据和建议。
- 可以说明哪些发现不是生产热路径问题。

## 测试边界

- 不运行自动化测试。
- 记录扫描命令作为可复核证据。

## 当前状态

Done

## 实现结果

- 已确认 `GatewayChatRuntime.execute(...)` 与 `GatewayResourceExecutor` 同步契约是主要异步边界风险。
- 已确认 `OpenAiNativeGatewayChatRuntime`、`OllamaGatewayChatRuntime`、`EmbeddingsGatewayResourceExecutor`、`EmbedRerankNativeGatewayResourceExecutor`、`GatewayOpenAiPassthroughService` 等生产热路径存在阻塞调用。
- 已将详细证据写入 `docs/reports/REP-20260525-007-async-boundary-sync-code-audit.md`。

## 验证情况

- 已执行生产代码阻塞调用扫描。
- 已人工复核公开网关入口和核心执行链路。
