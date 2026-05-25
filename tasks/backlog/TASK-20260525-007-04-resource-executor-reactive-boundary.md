# TASK-20260525-007-04 Resource Executor 响应式边界改造

## 类型

子任务 / task spec

## 背景

异步边界排查确认 `GatewayResourceExecutor` JSON/Binary 契约是同步 `ResponseEntity`，多个资源执行器在公开资源接口中使用 `WebClient.block()` 或同步 SDK。

## 目标

- 将 `GatewayResourceExecutor` 的 JSON/Binary 执行契约改为 `Mono<ResponseEntity<...>>`。
- 改造 `GatewayResourceExecutionService` 对 executor 的编排方式。
- 消除 embeddings、rerank、OpenAI passthrough 和 resource lifecycle 热路径中的 `block()`。
- 保持 multipart 已有 reactive 入口语义。

## 非目标

- 不重写所有 provider SDK。
- 不新增资源协议能力。
- 不调整公开响应结构。

## 上游来源

- `docs/requirements/REQ-20260525-007-async-boundary-sync-code-audit.md`
- `docs/reports/REP-20260525-007-async-boundary-sync-code-audit.md`

## 输入

- `GatewayResourceExecutor`
- `GatewayResourceExecutionService`
- `EmbeddingsGatewayResourceExecutor`
- `EmbedRerankNativeGatewayResourceExecutor`
- `GatewayOpenAiPassthroughService`
- `GatewayEmbeddingExecutionService`
- 相关 OpenAI/Gemini/Anthropic resource controller。

## 输出

- reactive resource executor 契约。
- resource controller reactive response。
- 同步 SDK 的显式阻塞隔离。

## 影响范围

- Embeddings。
- Rerank。
- Audio/Image/File resource passthrough。
- Responses remote lifecycle。

## 依赖

- route selection 和 credential material resolution。
- trace detail / observability 写入。

## 风险

- resource executor 涉及面广，迁移需要分阶段完成。
- 二进制和 multipart 响应需要特别验证 content-type、headers 和错误映射。

## 验收标准

- 资源执行器热路径不再通过 `block()` 同步等待 WebClient。
- JSON、Binary、Multipart 三类响应均保持兼容。
- 资源执行相关定向测试通过。

## 测试边界

- Embeddings/rerank executor tests。
- GatewayResourceExecutionService tests。
- Audio/Image/File controller tests。
- 建议新增 no-block 静态扫描测试。

## 关联文档

- `docs/reports/REP-20260525-007-async-boundary-sync-code-audit.md`

## 当前状态

Backlog

