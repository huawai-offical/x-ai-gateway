# OpenAI Responses Tools 兼容边界

## 当前结论

`/v1/responses` 当前执行 `function` tools，并已为 `file_search` 建立 gateway-local Vector Store 绑定基线。`file_search` 会先校验本地 `vector_store_ids`，复用本地 Vector Store Search 结果注入上下文，再移除 hosted tool，避免把本地 `vs_...` 误透传给上游。其它 hosted/MCP/custom/side-effect tools 仍会被显式拒绝，并返回 OpenAI-style `invalid_request_error`，避免旧行为中“请求携带工具但 mapper 静默跳过”的假执行。

官方 Responses 文档把 tools 分为 built-in tools、MCP tools 和 function/custom tools；`tool_choice` 还可强制选择 hosted tool、MCP、custom、apply_patch 或 shell。本 gateway 当前只把 `function` 交给 canonical tool calling，`file_search` 仅做本地检索上下文注入，不声明 OpenAI hosted `file_search_call` lifecycle；其它 side effect 或外部访问工具继续用 registry 固定拒绝边界。

## Compatibility Matrix

| Tool type | 类别 | 当前状态 | 当前边界 | 后续归属 |
| --- | --- | --- | --- | --- |
| `function` | custom-code | Supported | 映射为 canonical function tool definitions，由现有 provider tool calling 路径执行。 | 已支持 |
| `web_search_preview` / `web_search_preview_2025_03_11` | hosted | Blocked | 需要 OpenAI Direct native route、成本预算、外部访问审计和结果透传。 | `TASK-20260514-019` |
| `file_search` | hosted | Local-bound | 校验当前 Distributed Key 下的本地 `vector_store_ids`，复用本地 Vector Store Search 结果注入 `instructions`，并移除 hosted tool；不生成 hosted `file_search_call` lifecycle。 | `TASK-20260518-003` / `TASK-20260514-023` |
| `computer_use_preview` | hosted | Blocked | 涉及远程操作、审批和安全审计。 | `TASK-20260514-019` |
| `code_interpreter` | hosted | Blocked | 依赖 Containers 与 Code Interpreter 文件生命周期。 | `TASK-20260514-024` |
| `image_generation` | hosted | Blocked | 需要原生 Responses hosted tool result passthrough。 | `TASK-20260514-019` |
| `mcp` | mcp | Blocked | 需要 server allowlist、审批、调用与结果回填。 | `TASK-20260514-019` |
| `custom` | custom | Blocked | 需要 `custom_tool_call` 输出生命周期。 | `TASK-20260514-019` |
| `apply_patch` / `shell` / `local_shell` | side-effect | Blocked | 需要工作区、审批、审计与隔离策略。 | `TASK-20260514-019` |

## 请求行为

- `function` tools：映射为 canonical function tool definitions，由现有 provider tool calling 路径执行。
- `file_search` tools：要求 `vector_store_ids` 是非空 string array；校验并检索本地 Vector Store 后，把结果作为 `Local file_search context` 注入 `instructions`，再移除 hosted tool。
- `tool_choice.type=file_search`：拒绝请求；本地基线不支持强制 hosted `file_search_call`。
- `tool_choice.type=allowed_tools` 且限定 `file_search`：拒绝请求，避免声明强制 hosted tool 执行。
- 其它非 `function`/`file_search` tool type：拒绝请求。
- 未知 tool type：拒绝请求，并归入 `TASK-20260514-019` 的后续工具生态治理。

## 验证

本边界由以下测试覆盖：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesFileSearchBindingServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceVectorStoreSearchTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

## 关联

- [REQ-20260516-014](requirements/REQ-20260516-014-openai-responses-tool-registry-boundary.md)
- [REQ-20260518-003](requirements/REQ-20260518-003-openai-responses-file-search-local-vector-store-binding.md)
- [TASK-20260516-014](../tasks/done/TASK-20260516-014-openai-responses-tool-registry-boundary.md)
- [TASK-20260518-003](../tasks/done/TASK-20260518-003-openai-responses-file-search-local-vector-store-binding.md)
- [TASK-20260514-019](../tasks/backlog/TASK-20260514-019-openai-conversations-webhooks-tools.md)
- [TASK-20260514-023](../tasks/backlog/TASK-20260514-023-openai-vector-stores-full-stack.md)
