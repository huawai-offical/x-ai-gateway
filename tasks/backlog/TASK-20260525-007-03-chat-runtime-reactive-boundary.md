# TASK-20260525-007-03 Chat Runtime 响应式边界改造

## 类型

子任务 / task spec

## 背景

异步边界排查确认公开 Chat/Responses 非流式热路径仍通过同步 `GatewayChatRuntime.execute(...)` 执行上游 HTTP、数据库和响应编码，破坏 WebFlux 端到端异步承诺。

## 目标

- 将 chat runtime 非流式执行契约从同步 `CanonicalResponse` 迁移为 `Mono<CanonicalResponse>`。
- 将 `GatewayChatExecutionService` 非流式公开执行入口改为 reactive pipeline。
- 将 `/v1/chat/completions`、`/v1/responses` 非流式 controller 返回改为 `Mono<ResponseEntity<?>>`。
- 移除 `OpenAiNativeGatewayChatRuntime` 与 `OllamaGatewayChatRuntime` 非流式热路径中的 `block()` / `HttpClient.send()`。

## 非目标

- 不改 streaming 输出协议。
- 不调整请求/响应 JSON 字段语义。
- 不迁移整个 JPA 栈。

## 上游来源

- `docs/requirements/REQ-20260525-007-async-boundary-sync-code-audit.md`
- `docs/reports/REP-20260525-007-async-boundary-sync-code-audit.md`

## 输入

- `GatewayChatRuntime`
- `GatewayChatExecutionService`
- `OpenAiNativeGatewayChatRuntime`
- `OllamaGatewayChatRuntime`
- `OpenAiChatCompletionsController`
- `OpenAiResponsesController`

## 输出

- reactive chat runtime 契约。
- 非流式公开 chat/responses reactive controller。
- 同步 runtime 的临时 elastic 隔离或原生 async HTTP 实现。

## 影响范围

- Chat Completions。
- Responses create。
- OpenAI-compatible raw/native runtime。
- Ollama runtime。

## 依赖

- 现有 route selection、credential resolution 和 observability 调用链。

## 风险

- 改动会触及核心公开网关路径，需要回归 streaming、idempotency、trace detail 和 usage。
- JPA 仍是同步栈时，reactive 外层需要明确阻塞隔离策略。

## 验收标准

- 公开非流式 chat/responses controller 不再在 event loop 上直接执行阻塞上游 HTTP。
- 生产代码热路径不再出现 `WebClient.block()` 或 `HttpClient.send()`。
- 现有 chat/responses 定向测试通过。

## 测试边界

- Chat Completions controller tests。
- Responses controller tests。
- runtime 定向 tests。
- 建议新增 no-block 静态扫描测试。

## 关联文档

- `docs/reports/REP-20260525-007-async-boundary-sync-code-audit.md`

## 当前状态

Backlog

