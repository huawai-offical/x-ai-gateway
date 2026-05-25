# OpenAI Responses Tools 兼容边界

## 当前结论

`/v1/responses` 当前执行 `function` tools。`file_search` 属于 OpenAI hosted tool，必须走 OpenAI Direct native route 与 hosted `file_search_call` 生命周期；非 native / canonical execution 路径返回 `native_hosted_tool_required`，不再校验本地 `vector_store_ids` 后注入 `Local file_search context` 并移除 hosted tool。其它 hosted/MCP/custom/side-effect tools 仍会被显式拒绝，并返回 OpenAI-style `invalid_request_error`，避免旧行为中“请求携带工具但 mapper 静默跳过”的假执行。

官方 Responses 文档把 tools 分为 built-in tools、MCP tools 和 function/custom tools；`tool_choice` 还可强制选择 hosted tool、MCP、custom、apply_patch 或 shell。本 gateway 当前只把 `function` 交给 canonical tool calling；`file_search` 不再用本地 Vector Store 检索伪装 hosted tool 成功，其它 side effect 或外部访问工具继续用 registry 固定拒绝边界。

## Compatibility Matrix

| Tool type | 类别 | 当前状态 | 当前边界 | 后续归属 |
| --- | --- | --- | --- | --- |
| `function` | custom-code | Supported | 映射为 canonical function tool definitions，由现有 provider tool calling 路径执行。 | 已支持 |
| `web_search_preview` / `web_search_preview_2025_03_11` | hosted | Blocked | 需要 OpenAI Direct native route、成本预算、外部访问审计和结果透传。 | `TASK-20260514-019` |
| `file_search` | hosted | Native-required | 需要 OpenAI Direct native route 与 hosted `file_search_call` lifecycle；非 native / canonical execution 路径返回 `native_hosted_tool_required`，不得用本地 Vector Store 检索伪装成功。 | `TASK-20260524-001-04` / `TASK-20260514-023` |
| `computer_use_preview` | hosted | Blocked | 涉及远程操作、审批和安全审计。 | `TASK-20260514-019` |
| `code_interpreter` | hosted | Out of scope | Containers 与 Code Interpreter 文件生命周期不属于当前功能性服务 API 必做范围。 | `ADR-0010` |
| `image_generation` | hosted | Blocked | 需要原生 Responses hosted tool result passthrough。 | `TASK-20260514-019` |
| `mcp` | mcp | Blocked | 需要 server allowlist、审批、调用与结果回填。 | `TASK-20260514-019` |
| `custom` | custom | Blocked | 需要 `custom_tool_call` 输出生命周期。 | `TASK-20260514-019` |
| `apply_patch` / `shell` / `local_shell` | side-effect | Blocked | 需要工作区、审批、审计与隔离策略。 | `TASK-20260514-019` |

## 请求行为

- `function` tools：映射为 canonical function tool definitions，由现有 provider tool calling 路径执行。
- `file_search` tools：保留在原始请求体中进入 native-required / Lossless Matrix 判断；非 OpenAI Direct native route 不执行本地检索绑定，返回 `native_hosted_tool_required`。
- `tool_choice.type=file_search`：同样按 hosted tool native-required 处理；非 native route 返回 `native_hosted_tool_required`。
- `tool_choice.type=allowed_tools` 且限定 `file_search`：同样按 hosted tool native-required 处理。
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
- [REQ-20260518-003](requirements/REQ-20260518-003-openai-responses-file-search-local-vector-store-binding.md)（历史本地绑定基线，已被 `TASK-20260524-001-04` 的 native-required 默认语义 supersede）
- [TASK-20260516-014](../tasks/done/TASK-20260516-014-openai-responses-tool-registry-boundary.md)
- [TASK-20260518-003](../tasks/done/TASK-20260518-003-openai-responses-file-search-local-vector-store-binding.md)（历史实现记录，当前公开 `/v1/responses` 默认不再调用本地绑定）
- [TASK-20260524-001-04](../tasks/in-progress/TASK-20260524-001-04-unsupported-capability-hard-fail.md)
- [TASK-20260514-019](../tasks/done/TASK-20260514-019-openai-conversations-webhooks-tools.md)
- [TASK-20260514-023](../tasks/done/TASK-20260514-023-openai-vector-stores-full-stack.md)
