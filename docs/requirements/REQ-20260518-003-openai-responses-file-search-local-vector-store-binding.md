# REQ-20260518-003 OpenAI Responses File Search 本地 Vector Store 绑定基线

状态：Done
日期：2026-05-18
上游来源：[TASK-20260514-023](../../tasks/backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 背景

`TASK-20260518-002` 已补齐 `POST /v1/vector_stores/{vectorStoreId}/search` 的本地文本检索基线，但 `/v1/responses` 的 `file_search` tool 仍被 `OpenAiResponsesToolRegistry` 整体拒绝。父任务 `TASK-20260514-023` 的验收标准要求 Responses `file_search` tool 能引用 `vector_store_ids`，因此下一步需要先建立本地资源引用、租户权限校验和可测试的上下文注入边界。

官方 file search tool 以 `type=file_search` 通过 `vector_store_ids` 引用 Vector Stores，并可带 `max_num_results`、`filters`、`ranking_options` 等检索参数。本项目当前没有真实 OpenAI 托管向量入库，也没有 hosted `file_search_call` 结果生命周期，因此本轮只做 gateway-local baseline：验证本地 `vector_store_ids`，复用本地 search 结果，把检索片段注入 Responses 上下文，再移除 hosted file_search tool，避免静默丢工具或虚假宣称 hosted 执行。

## 目标

- `/v1/responses` 接收 `tools[].type=file_search` 的本地基线请求。
- 解析并校验 `vector_store_ids`：
  - 必须是非空 string array。
  - 每个 `vs_...` 必须属于当前 Distributed Key，且未被删除。
- 复用 `GatewayAsyncResourceService.searchVectorStore` 对每个本地 Vector Store 执行检索。
- 从 `instructions` 与 `input` 中提取查询文本，作为本地 search query。
- 将 top results 以清晰的 local file_search context 注入 `instructions`，让后续 canonical execution 能看到检索内容。
- 从传给 canonical mapper 的请求中移除 `file_search` tool，保留 function tools，避免 mapper 或 provider 把本地 `vs_...` 当作 OpenAI hosted vector store。
- 当 `tool_choice` 强制 `type=file_search` 时返回清晰错误；本地基线不声明 hosted tool call lifecycle。
- 更新 Responses tools 兼容文档、public docs、provider catalog、父任务和测试。

## 范围

- `OpenAiResponsesController`
- `OpenAiResponsesRequestMapper`
- `OpenAiResponsesToolRegistry`
- 新增 Responses file search binding/preflight service
- `GatewayAsyncResourceService.searchVectorStore` 复用
- Public docs、provider catalog、报告与任务状态
- Controller/service/docs/provider catalog 测试

## 非目标

- 不实现 OpenAI hosted `file_search_call` 输出对象。
- 不把本地 `vs_...` 透传到 OpenAI Direct upstream。
- 不实现真实 embedding、semantic rerank、query rewrite 或向量数据库。
- 不改变 function tools 的既有 canonical execution。
- 不支持 `tool_choice.type=file_search` 的强制 hosted 调用。

## 方案

- 新增 `OpenAiResponsesFileSearchBindingService`：
  - 检测 `tools[]` 中的 `type=file_search`。
  - 校验 `vector_store_ids`、`max_num_results`、`filters` 与 `ranking_options.score_threshold`。
  - 调用 `GatewayAsyncResourceService.searchVectorStore` 获取本地结果。
  - 生成 `Local file_search context` 文本，并追加到 `instructions`。
  - 移除 file_search tool，保留 function tool。
  - 若 `tool_choice` 强制 file_search，拒绝请求，避免假执行。
- `OpenAiResponsesController.createResponse` 在 idempotency replay 之后、canonical mapper 之前执行 binding。
- `OpenAiResponsesToolRegistry` 把 `file_search` 标记为本地 preflight 支持状态，同时仍保留其它 hosted/MCP/side-effect tools 的拒绝边界。

## 风险

- 本地上下文注入不等价于 OpenAI hosted file_search result lifecycle，必须在 docs/catalog 中明确。
- 查询文本由本地从 `instructions/input` 提取，和 OpenAI query rewrite/semantic retrieval 有差异。
- 若不移除 `file_search` tool，可能让下游误用本地 vector store id；必须测试确保不会透传。

## 验收标准

- `/v1/responses` 携带 `tools[].type=file_search` 和有效本地 `vector_store_ids` 时，能够通过本地绑定并调用 gateway execution。
- 传给 gateway execution 的 canonical request 不再包含 file_search hosted tool，且 provider extensions 中包含注入后的 local file_search context。
- 无效、缺失或跨租户 `vector_store_ids` 返回 OpenAI-style `invalid_request_error`。
- `tool_choice.type=file_search` 返回清晰错误。
- Responses tools compatibility docs、public docs、provider catalog 和父任务状态同步。

## 测试边界

- `OpenAiResponsesFileSearchBindingServiceTests`
- `OpenAiResponsesControllerTests`
- `PublicDocsBundleServiceTests`
- `ProviderCatalogLoaderTests`
- 相关 Vector Store Search 回归

## 关联文档

- [TASK-20260514-023](../../tasks/backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)
- [REQ-20260518-002](REQ-20260518-002-openai-vector-store-search-local-text-baseline.md)
- OpenAI File Search guide：
  - https://platform.openai.com/docs/guides/tools-file-search

## 当前状态

- 2026-05-18：已完成本地 Vector Store 绑定基线。`/v1/responses` 可接收 `tools[].type=file_search`，校验当前 Distributed Key 下的本地 `vector_store_ids`，复用本地 Vector Store Search 结果注入 `Local file_search context`，并在进入 canonical mapper/provider 前移除 hosted `file_search` tool。

## 实现结果

- 新增 `OpenAiResponsesFileSearchBindingService`，负责 `file_search` tool 检测、`vector_store_ids` 校验、本地 search request 组装、上下文注入和 hosted tool 移除。
- `OpenAiResponsesController.createResponse` 在 idempotency replay 之后、canonical mapper 之前执行本地 binding，避免本地 `vs_...` 被误传给 OpenAI Direct 或兼容站点。
- `OpenAiResponsesToolRegistry` 将 `file_search` 标记为本地绑定状态，其它 hosted/MCP/custom/side-effect tools 仍保持显式拒绝。
- Public docs、OpenAPI snapshot、provider catalog、兼容性报告与父任务已同步区分“本地 file_search context injection”和“OpenAI hosted file_search_call lifecycle”。

## 验证记录

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesFileSearchBindingServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreSearchTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

结果：通过。

## 后续建议

- 真实向量入库、semantic retrieval、rerank/query rewrite、hosted `file_search_call` lifecycle 和真实 OpenAI Direct smoke 继续归属 `TASK-20260514-023` 后续切片。
