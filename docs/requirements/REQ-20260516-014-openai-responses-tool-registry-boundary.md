# REQ-20260516-014 OpenAI Responses Tools Registry 与非 function Tool 显式边界

## 背景

OpenAI Responses API 的 `tools` 不只包含 `function`，官方文档还列出 built-in tools、MCP tools、custom tools，以及 `tool_choice` 中的 hosted tool、MCP、custom、apply_patch、shell 等选择方式。当前 `OpenAiResponsesRequestMapper` 对非 `function` tool 采用 `continue` 跳过，客户端会误以为 `web_search_preview`、`file_search`、`mcp`、`code_interpreter` 等工具已经参与执行，实际 canonical execution 并不会调用这些工具。

该行为会产生兼容性错觉和安全风险：搜索、文件、MCP、代码解释器、shell/apply_patch 等工具要么涉及额外资源生命周期，要么涉及外部访问和 side effect 审计，不能被静默忽略。

## 目标

- 建立 Responses tools compatibility registry，明确每类 tool 的当前状态、执行边界和后续任务归属。
- `function` tools 继续映射到 canonical tool definitions。
- 非 `function` tool definition 不再静默跳过；当前 gateway canonical execution 路径统一返回 OpenAI-style `invalid_request_error`。
- `tool_choice` 中强制选择非 `function`、`allowed_tools` 中包含非 `function` 时，也显式拒绝。
- 公开 docs bundle、public OpenAPI、provider catalog 与 Markdown 文档同步声明 Responses tools 边界。

## 非目标

- 不在本切片实现 hosted `web_search_preview`、`file_search`、`computer_use_preview`、`code_interpreter`、`image_generation` 的真实执行。
- 不实现 MCP server 调用、审批、结果回填或 connector 授权。
- 不实现 custom tool call 的完整输出生命周期。
- 不实现 Conversations/Items lifecycle，仍归属 `TASK-20260514-019` 后续切片。
- 不实现 Vector Stores 或 Containers；Vector Stores 已由 `TASK-20260514-023` 收口，Containers 不属于当前功能性服务 API 必做范围。

## 范围

- `OpenAiResponsesRequestMapper` 的 `tools` 与 `tool_choice` 校验。
- 新增 Responses tool registry。
- Responses controller tests。
- `PublicDocsBundleService`、`docs/openapi/public-openapi.json`、`docs/public-api-compatibility.md`、provider catalog。
- `TASK-20260514-019` 与 `tasks/index.md` 状态回写。

## 风险

- 部分客户端此前发送非 `function` tools 会从“静默忽略后返回普通文本”变为 400；这是兼容性修正而不是功能回退，因为旧行为没有真实执行工具。
- OpenAI Direct 原生 passthrough 对这些 hosted/MCP/custom tools 的完整支持需要在 route selection 后做受控放行；本切片先选择安全默认拒绝，避免错误路由到第三方 OpenAI-compatible 站点。
- 错误提示必须足够具体，方便用户知道可用替代方案和后续任务边界。

## 验收标准

- `tools:[{type:"function", ...}]` 保持可用。
- `tools:[{type:"web_search_preview"}]` 返回 400 OpenAI-style error，不再进入 gateway execution。
- `tool_choice:{type:"mcp", ...}` 返回 400 OpenAI-style error。
- `tool_choice:{type:"allowed_tools", tools:[{type:"mcp"}]}` 返回 400 OpenAI-style error。
- public OpenAPI 的 `/v1/responses` request body 声明 `tools` 与 `tool_choice`，并写清非 function tools 的当前边界。
- provider catalog 和 public docs bundle 包含 `openai.responses-tool-registry-boundary` conformance check。

## 测试边界

- 不依赖真实 OpenAI key。
- 不调用上游 OpenAI API。
- 以 controller/service 文档测试覆盖本地显式拒绝、公开文档和 OpenAPI snapshot。

## 官方参考

- OpenAI Responses create API Reference：`https://developers.openai.com/api/reference/resources/responses/methods/create`
- OpenAI Conversations create API Reference：`https://developers.openai.com/api/reference/resources/conversations/methods/create`
- OpenAI Webhook events API Reference：`https://developers.openai.com/api/reference/resources/webhooks`

## 关联任务

- [TASK-20260516-014](../../tasks/done/TASK-20260516-014-openai-responses-tool-registry-boundary.md)
- [TASK-20260514-019 OpenAI Conversations、Webhooks 与 Responses 工具生态](../../tasks/done/TASK-20260514-019-openai-conversations-webhooks-tools.md)
- [TASK-20260514-023 OpenAI Vector Stores 全栈兼容](../../tasks/done/TASK-20260514-023-openai-vector-stores-full-stack.md)
- Containers 与 Code Interpreter 文件：当前按 [ADR-0010](../decisions/ADR-0010-functional-service-api-scope.md) 判定为非核心官方 API，不再保留独立 Backlog 任务。

## 当前状态

Done。Responses tools 的安全边界与文档事实源已闭环；后续继续推进 Conversations lifecycle 与 hosted/MCP/custom tool 真实执行。
