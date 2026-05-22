# TASK-20260516-014 OpenAI Responses Tools Registry 与非 function Tool 显式边界

状态：Done
优先级：High
类型：子任务切片
父任务：[TASK-20260514-019](../done/TASK-20260514-019-openai-conversations-webhooks-tools.md)
上游来源：[REQ-20260516-014](../../docs/requirements/REQ-20260516-014-openai-responses-tool-registry-boundary.md)、[TASK-20260514-013](../done/TASK-20260514-013-openai-chat-responses-native-parity.md)

## 背景

OpenAI Responses 官方 `tools` 支持 function、built-in tools、MCP tools、custom tools 等类别。当前 gateway 的 Responses mapper 只映射 `function`，但对非 `function` tool 静默跳过，容易造成客户以为工具已执行、实际只是普通文本推理的错误结果。

## 目标

- 新增 Responses tools compatibility registry，固定 `function`、built-in、MCP、custom、shell/apply_patch 等类型的当前支持状态。
- `function` tools 继续进入 canonical `CanonicalToolDefinition`。
- 非 `function` tools 和非 `function` tool_choice 显式返回错误，不再静默忽略。
- 更新 provider catalog、public docs bundle、public OpenAPI 和 Markdown 兼容说明。
- 用单元测试证明 function 仍可用，非 function tool/tool_choice 被拒绝且不执行 gateway。

## 非目标

- 不实现真实 hosted tool、MCP、custom tool、code interpreter、shell/apply_patch 执行。
- 不实现 Conversations/Items lifecycle。
- 不实现 Vector Stores、Containers 或 Code Interpreter 文件资源。
- 不引入真实 OpenAI smoke。

## 输入

- OpenAI Responses create API Reference。
- `OpenAiResponsesRequestMapper`
- `OpenAiResponsesControllerTests`
- `PublicDocsBundleService`
- `provider-catalog.json`

## 输出

- Responses tool registry 与 mapper 校验。
- Controller tests、public docs tests、OpenAPI snapshot tests。
- 公开 Markdown tool compatibility matrix。
- 本地任务和上游父任务状态回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/main/resources/provider-catalog.json`
- `docs/openapi/public-openapi.json`
- `docs/public-api-compatibility.md`
- `docs/openai-responses-tools-compatibility.md`
- `tasks/done/TASK-20260514-019-openai-conversations-webhooks-tools.md`

## 依赖

- `TASK-20260515-017` 至 `TASK-20260516-003` 已建立 Responses native/local lifecycle 双轨基础。
- `GlobalApiExceptionHandler` 已对 OpenAI path 返回 OpenAI-style error envelope。

## 风险

- 旧客户端发送非 `function` tools 会从静默忽略变为 400；需要在错误消息和公开文档中说明这是为了避免假执行。
- 后续如果要支持 OpenAI Direct native hosted tool passthrough，需要在 route selection 后增加 provider/site/key 级能力判定，不能在 mapper 层无条件放行。

## 验收标准

- `function` tool 映射和 stream function call 测试保持通过。
- `web_search_preview`、`mcp`、`allowed_tools` 中的非 function tool 会返回 400，错误体为 OpenAI-style `invalid_request_error`。
- provider catalog 包含 `openai.responses-tool-registry-boundary`。
- public docs bundle 与 public OpenAPI 明确 `/v1/responses` 当前只支持 function tools，非 function tool 会被显式拒绝。

## 测试边界

- `OpenAiResponsesControllerTests`
- `PublicDocsBundleServiceTests`
- `ProviderCatalogLoaderTests`
- `PublicOpenApiSnapshotTests`
- 不执行真实 OpenAI API。

## 关联文档

- [REQ-20260516-014](../../docs/requirements/REQ-20260516-014-openai-responses-tool-registry-boundary.md)
- [TASK-20260514-019](../done/TASK-20260514-019-openai-conversations-webhooks-tools.md)
- [docs/openai-responses-tools-compatibility.md](../../docs/openai-responses-tools-compatibility.md)
- OpenAI Responses create API Reference：`https://developers.openai.com/api/reference/resources/responses/methods/create`

## 当前状态

- 2026-05-16：已闭环。Responses tools registry、非 function tool 显式拒绝、公开文档和 provider catalog 已同步。

## 实现结果

- 新增 `OpenAiResponsesToolRegistry`：
  - `function` 标记为 `SUPPORTED`，继续进入 canonical tool calling。
  - `web_search_preview`、`web_search_preview_2025_03_11`、`file_search`、`computer_use_preview`、`code_interpreter`、`image_generation`、`mcp`、`custom`、`apply_patch`、`shell`、`local_shell` 标记为 `BLOCKED`。
  - 未知 tool type 也按 `BLOCKED` 处理，避免静默忽略。
- `OpenAiResponsesRequestMapper`：
  - `tools` 中非 `function` tool 会直接抛出明确错误。
  - `tool_choice` 中非 `function` 强制选择会直接抛出明确错误。
  - `tool_choice.type=allowed_tools` 且列表内包含非 `function` tool 时直接拒绝。
  - `function` tool 缺少 `name` 时直接拒绝，不再静默跳过。
- `OpenAiResponsesControllerTests` 增加三条拒绝回归：
  - `web_search_preview` tool 被拒绝。
  - `tool_choice.type=mcp` 被拒绝。
  - `allowed_tools` 中的 `code_interpreter` 被拒绝。
- `provider-catalog.json` 新增 `openai.responses-tool-registry-boundary` conformance check，并在 OpenAI Direct unsupported features 中声明非 function tools 的当前边界。
- `PublicDocsBundleService`、`docs/openapi/public-openapi.json`、`docs/public-api-compatibility.md` 与 [openai-responses-tools-compatibility.md](../../docs/openai-responses-tools-compatibility.md) 已同步公开说明。
- 上游父任务与审计报告已回写；Conversations lifecycle 已由 `TASK-20260516-015` 后续闭环，Webhooks controller 与 hosted/MCP/custom tool 真实执行仍是后续切片。

## 验证记录

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"
```

结果：通过。

## 遗留边界

- hosted `web_search_preview`、`file_search`、`computer_use_preview`、`code_interpreter`、`image_generation` 未实现真实执行。
- MCP/custom tools 未实现 server allowlist、审批、调用与结果回填。
- `shell`、`apply_patch` 等 side effect tools 未放行，后续必须先设计工作区隔离、审批和审计。
- Conversations/Items lifecycle 与 Webhooks controller/event 落库仍归属 `TASK-20260514-019` 后续切片。
