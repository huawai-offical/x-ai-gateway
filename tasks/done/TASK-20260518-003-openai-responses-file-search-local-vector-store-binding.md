# TASK-20260518-003 OpenAI Responses File Search 本地 Vector Store 绑定基线

状态：Done
优先级：High
类型：子任务
父任务：[TASK-20260514-023](../done/TASK-20260514-023-openai-vector-stores-full-stack.md)
上游来源：[REQ-20260518-003](../../docs/requirements/REQ-20260518-003-openai-responses-file-search-local-vector-store-binding.md)

## 背景

Vector Stores 本地 lifecycle、files、file batches、file content 和 search 已建立基线，但 Responses `file_search` tool 仍完全阻断。父任务要求 file_search 能引用 `vector_store_ids`，本切片先把本地 Vector Store 引用、权限校验和检索结果上下文注入打通。

## 目标

- 接收 `/v1/responses` 中的 `tools[].type=file_search` 本地基线请求。
- 校验当前 Distributed Key 下的 `vector_store_ids`。
- 复用本地 Vector Store Search 结果注入 Responses `instructions`。
- 移除传给 canonical mapper/provider 的 hosted `file_search` tool，避免误透传。
- 保留 function tools 既有行为。

## 非目标

- 不实现 OpenAI hosted `file_search_call` lifecycle。
- 不实现真实向量入库、semantic retrieval、rerank 或 query rewrite。
- 不支持强制 `tool_choice.type=file_search`。
- 不改其它非 function tools 的拒绝策略。

## 输入

- `OpenAiResponsesController`
- `OpenAiResponsesToolRegistry`
- `OpenAiResponsesRequestMapper`
- `GatewayAsyncResourceService.searchVectorStore`
- `TASK-20260514-023`

## 输出

- Responses file_search binding/preflight service。
- Controller 集成与测试。
- Responses tools compatibility docs、public docs、provider catalog 更新。
- 父任务剩余切片更新。

## 影响范围

- OpenAI Responses ingress。
- Vector Store local search。
- Responses tool registry。
- Public compatibility docs 和 provider catalog。

## 依赖

- [TASK-20260518-002](../done/TASK-20260518-002-openai-vector-store-search-local-text-baseline.md)

## 风险

- 本地 context injection 容易被误解为 hosted file_search，需要文档明确。
- 本地 query extraction 与 OpenAI query rewrite 语义不同。
- 不能把本地 `vs_...` 透传给 OpenAI Direct。

## 验收标准

- 有效本地 `vector_store_ids` 可被 `/v1/responses` file_search 请求引用。
- 无效 `vector_store_ids`、空数组和强制 file_search tool_choice 均有清晰错误。
- file_search 搜索结果会进入 canonical request 的 provider extensions/instructions。
- file_search tool 不会出现在 canonical function tools 中。
- 文档、provider catalog 和父任务同步。

## 测试边界

- `OpenAiResponsesFileSearchBindingServiceTests`
- `OpenAiResponsesControllerTests`
- `PublicDocsBundleServiceTests`
- `ProviderCatalogLoaderTests`

## 关联文档

- [REQ-20260518-003](../../docs/requirements/REQ-20260518-003-openai-responses-file-search-local-vector-store-binding.md)
- [TASK-20260514-023](../done/TASK-20260514-023-openai-vector-stores-full-stack.md)

## 当前状态

- 2026-05-18：已完成并归档。

## 实现结果

- 新增 `OpenAiResponsesFileSearchBindingService`，在 Responses create 进入 canonical mapper 之前执行本地 `file_search` binding。
- 支持 `tools[].type=file_search` 读取非空 `vector_store_ids`，复用 `GatewayAsyncResourceService.searchVectorStore` 获取本地 search 结果。
- 将结果以 `Local file_search context` 注入 `instructions`，同时移除 hosted `file_search` tool，保留 function tools 既有执行行为。
- 对缺失/非法 `vector_store_ids`、强制 `tool_choice.type=file_search`、`allowed_tools` 限定 `file_search` 返回清晰 OpenAI-style 错误。
- 同步 `OpenAiResponsesToolRegistry`、Public docs、OpenAPI snapshot、provider catalog、兼容性文档、深度审计报告和父任务进度。

## 验证记录

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesFileSearchBindingServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreSearchTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

结果：通过。

## 遗留问题

- 本任务不实现真实向量入库、semantic retrieval、rerank/query rewrite。
- 本任务不生成 OpenAI hosted `file_search_call` lifecycle。
- 真实 OpenAI Direct smoke 继续由父任务后续切片处理。
