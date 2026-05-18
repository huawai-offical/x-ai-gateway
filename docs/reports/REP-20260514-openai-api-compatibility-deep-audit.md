# REP-20260514 OpenAI API 完整兼容性深度审计

## 结论

当前项目不能判定为“OpenAI API 已完全实现”，也不能判定为“OpenAI API 参数完全兼容”。

更准确的状态是：项目已经实现了 OpenAI-style 核心接入面，覆盖 `/v1/chat/completions`、OpenAI Direct 原生 `/v1/responses` create/stream/lifecycle route-hint、Responses `file_search` 本地 Vector Store 绑定、`/v1/conversations` local lineage、`/v1/vector_stores` local lifecycle、file attachment、file content read、本地文本 search 与 file batch、`/v1/webhooks/openai` ingress event persistence、Embeddings、Audio、Images、Files、Uploads、Batches、Fine-tuning、Moderations、Models 和 Realtime client secrets 的一部分；但 Chat/Responses 参数仍是选择性映射或受控保真，官方资源族中 Vector Stores 真实向量入库/语义向量检索、Evals、Containers、Administration、Chat Completions 对象生命周期、Realtime calls、Audio voice consents、Video 官方面等未形成完整公开入口或兼容声明。

## 官方事实源

- OpenAI API Reference 明确描述其包含 REST、streaming 和 realtime APIs，并在导航中列出 Responses、Conversations、Audio、Images、Embeddings、Evals、Fine-tuning、Batches、Files、Uploads、Models、Moderations、Vector Stores、Containers、Realtime、Administration 和 Chat Completions 等资源族。
- Chat Completions create 参数面包含 `metadata`、`modalities`、`n`、`parallel_tool_calls`、`prediction`、`presence_penalty`、`prompt_cache_key`、`prompt_cache_retention`、`reasoning_effort`、`response_format`、`safety_identifier`、`seed`、`service_tier`、`stop`、`store`、`stream_options`、`top_logprobs`、`top_p`、`user`、`verbosity`、`web_search_options` 等字段。
- Responses create 参数面包含 `background`、`conversation`、`include`、`max_tool_calls`、`parallel_tool_calls`、`previous_response_id`、`prompt`、`prompt_cache_key`、`reasoning`、`safety_identifier`、`service_tier`、`store`、`stream_options`、`text`、`tool_choice`、`tools`、`top_logprobs`、`truncation`、`user` 等字段，并支持内置工具、MCP tools、custom tools、file search、web search、computer use、code interpreter、image generation 等工具类别。
- 官方 API 还公开了 Batch list、Fine-tuning events/checkpoints/pause/resume、Models delete、Vector Stores 全套对象与文件批次、Containers 文件、Realtime calls、Administration usage/costs/users/projects/API keys 等端点。

来源：

- https://developers.openai.com/api/reference/
- https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create
- https://developers.openai.com/api/reference/resources/responses/methods/create
- https://developers.openai.com/api/reference/resources/batches/methods/create
- https://developers.openai.com/api/reference/resources/fine_tuning/subresources/jobs/methods/create

## 本地实现现状

| API 面 | 当前状态 | 证据 | 判断 |
| --- | --- | --- | --- |
| Chat Completions | 已有入口和 canonical 映射 | `OpenAiChatCompletionRequest` 只定义 `model/messages/tools/tool_choice/reasoning/reasoning_effort/temperature/max_tokens/stream`；mapper 只下发这些字段 | 部分兼容，参数不全量 |
| Responses | 已有入口、store/get/delete、本地编码和 streaming | `OpenAiResponsesController` 已补 input_items/input_tokens/compact/remote lifecycle、无 lineage route hint 等切片；`OpenAiResponsesRequestMapper` 仅执行 function tools，非 function tool 已由 `TASK-20260516-014` 改为显式拒绝；原 body 保留在 `providerExtensions` | OpenAI Direct create/stream/lifecycle 已 native-first；非 function hosted/MCP/custom 真实执行仍未实现 |
| Conversations | 本地 lineage lifecycle | `TASK-20260516-015` 已补 `POST/GET/POST/DELETE /v1/conversations` 与 `POST/GET/GET/DELETE /v1/conversations/{conversationId}/items`，conversation 与 item 分开保存到 `gateway_async_resource` | 本地对象生命周期可用；暂不声明 OpenAI Direct 上游 passthrough |
| Vector Stores | 本地 lifecycle、attachment、file content read、本地 text search、Responses file_search 本地绑定与 file batch 基线 | `TASK-20260517-003` 已补 `POST/GET /v1/vector_stores` 与 `GET/POST/DELETE /v1/vector_stores/{vectorStoreId}`；`TASK-20260517-004` 已补 `POST/GET /v1/vector_stores/{vectorStoreId}/files` 与 `GET/DELETE /v1/vector_stores/{vectorStoreId}/files/{fileId}`；`TASK-20260517-005` 已补 file batch create/retrieve/cancel/list files；`TASK-20260518-001` 已补 file content 本地文本页读取；`TASK-20260518-002` 已补 `POST /v1/vector_stores/{vectorStoreId}/search` 本地文本检索；`TASK-20260518-003` 已补 Responses `file_search` 本地 Vector Store 绑定 | create/list/retrieve/update/delete、本地 file attachment、file content read、本地 text search、Responses file_search 本地绑定与 file batch 可用；真实向量入库、语义向量检索和 hosted `file_search_call` lifecycle 仍未实现 |
| Webhooks | 接收入口、验签与事件落库 | `TASK-20260516-016` 已补 `POST /v1/webhooks/openai`，复用 Standard Webhooks verifier，并把合法 event 保存为 `WEBHOOK_EVENT`；重复 delivery 或重复 event id 幂等返回 `duplicate=true` | inbound event persistence 可用；暂不实现 Dashboard endpoint 管理或 event 业务副作用 |
| OpenAI native runtime | OpenAI Direct Responses create/stream/lifecycle 优先走原生上游 | 非 OpenAI Direct 或 route 不可用时仍保留 canonical/local fallback；Responses-only 字段在兼容站点上仍按 provider 能力保真或显式拒绝 | Hosted/MCP/custom tools 和部分资源族仍需后续任务 |
| Embeddings | JSON passthrough/resource execution | `/v1/embeddings` 在 feature service 与 conformance matrix 中覆盖 | 基础兼容，需真实 smoke 与参数回归 |
| Audio | transcriptions/translations/speech | multipart transcription 只接收 `file/model/language/prompt/response_format/temperature`；speech JSON passthrough | 部分兼容，multipart 参数不全 |
| Images | generations/edits/variations | generations JSON passthrough；edits/variations multipart 只接收有限字段 | 部分兼容，image edit/variation 参数不全 |
| Files | list/create/get/content/delete | list 使用本地 catalog；create 支持 `file/purpose` | 基础生命周期可用，但 list filter/order/after 等官方行为不完整 |
| Uploads | create/get/parts/complete/cancel | 本地多了 GET upload；官方导航只列 create/cancel/complete/parts create | 接近但需对齐官方路径与响应语义 |
| Batches | create/get/cancel/list | `TASK-20260516-011` 已补 `GET /v1/batches`，返回当前 DistributedKey 下经 gateway 创建并持久化的 Batch lineage list | 基础生命周期可用；list 不代表上游组织全量历史 |
| Fine-tuning | create/list/get/cancel；events/checkpoints 本地 lineage 已由 `TASK-20260516-013` 补齐 | 不同步上游完整事件历史；controller 未包含 pause/resume/permissions | 仍缺 pause/resume/permissions/graders 与真实上游事件同步 |
| Models | list/get/delete | `TASK-20260516-012` 已补 `DELETE /v1/models/{model}`，仅删除当前 DistributedKey 下经 gateway fine-tuning/import 登记的本地 fine-tuned model registry | 基础 gateway registry delete 可用；真实上游 Owner role 删除 passthrough 未实现 |
| Realtime | client secrets | 只公开 `/v1/realtime/client_secrets`；官方还有 translations client secrets、calls、events | 部分兼容 |
| Evals | 未发现实现 | 官方资源族存在，本地无公开入口 | 缺失 |
| Containers | 未发现实现 | 官方资源族存在，本地无公开入口 | 缺失 |
| Administration | 未实现 OpenAI Admin API 兼容面 | 本地 admin 是自身管理后台，不是 OpenAI Admin API | 缺失 |
| Public OpenAPI | 已持续补齐核心公开面，但仍不是官方全量 spec | `docs/openapi/public-openapi.json` 已列 Chat/Responses/Conversations/Vector Stores/Webhooks/Batches/Models/Fine-tuning 局部面，仍未列全部 Embeddings、Audio、Images、Files、Uploads、Moderations、Realtime | 仍需完整 OpenAPI 生成与快照收敛 |
| Provider catalog | OpenAI preset 已改为 native-first 边界声明 | `provider-catalog.json` 已列 Responses tools、Vector Stores child resources、Fine-tuning、Models upstream delete、Containers、Evals、Administration、Realtime full calls 等未完成边界 | 声明已更清晰，仍需逐项实现 |

## 参数兼容性重点缺口

### Chat Completions

本地 `OpenAiChatCompletionRequest` 是强 DTO，未使用 raw JSON 全量保真。当前会直接丢失或无法结构化处理一批官方字段，包括但不限于：

- `max_completion_tokens`、`metadata`、`modalities`、`n`、`prediction`、`presence_penalty`、`prompt_cache_key`、`prompt_cache_retention`、`response_format`、`safety_identifier`、`seed`、`service_tier`、`stop`、`stream_options`、`store`、`top_logprobs`、`top_p`、`user`、`verbosity`、`web_search_options`。
- `tools` 目前 DTO 支持 function tool；官方 Chat 文档已出现 custom tool/grammar 等扩展形态，当前 mapper 不具备全量工具语义。
- `max_tokens` 仍存在，但官方文档已标注其被 `max_completion_tokens` 取代，且对 o-series 不兼容；项目当前仍主要映射 `max_tokens`。

### Responses

本地 Responses 的优势是保留了原始请求体到 `providerExtensions`，OpenAI Direct create/stream/lifecycle 已按 native-first 推进，但 hosted/MCP/custom tools 仍未等价于官方 Responses API：

- `OpenAiResponsesRequestMapper` 只把 `type=function` 的 tools 转为 canonical tools；`file_search` 已由 `TASK-20260518-003` 在 controller preflight 阶段绑定本地 `vector_store_ids` 并注入本地 search context，然后移除 hosted tool。`web_search_preview`、`computer_use_preview`、`code_interpreter`、`image_generation`、`mcp`、`custom`、`shell/apply_patch` 等仍由 `TASK-20260516-014` 显式拒绝，不再静默跳过；hosted/MCP/custom 真实执行仍未实现。
- OpenAI Direct 非流式 create 已返回上游原始 Responses JSON，`stream=true` 透传上游 SSE；非 OpenAI Direct 或 route 不可用时仍保留 canonical/local fallback。
- Responses lifecycle 已补 `input_items`、`input_tokens`、`cancel`、`compact`、远端 lineage passthrough 与无 lineage `model`/`X-AI-Gateway-OpenAI-Model` route hint；Conversations 及 Items 子资源已由 `TASK-20260516-015` 补为本地 lineage。Responses native runtime 后续重点转向 hosted/MCP/custom tools 真实执行。

## 公开声明风险

- `docs/openapi/public-openapi.json` 比实际代码窄，容易让客户以为只有 Chat/Responses；但 provider catalog 又说 OpenAI 覆盖多个资源族，二者不一致。
- `provider-catalog.json` 的 OpenAI preset 已显式列出未完成边界；当前仍缺 Vector Stores 真实向量入库/语义向量检索、Evals、Containers、Admin、Fine-tuning pause/resume/permissions、Models upstream owner-role delete passthrough 等。Batch list 已由 `TASK-20260516-011` 补齐为本地 lineage list，Models delete 本地 registry 边界已由 `TASK-20260516-012` 补齐，Fine-tuning events/checkpoints 本地 lineage 已由 `TASK-20260516-013` 补齐，Responses 非 function tools 的显式拒绝边界已由 `TASK-20260516-014` 补齐，Conversations local lifecycle 已由 `TASK-20260516-015` 补齐，Webhooks ingress/event persistence 已由 `TASK-20260516-016` 补齐，Vector Stores local lifecycle 已由 `TASK-20260517-003` 补齐，Vector Store Files 本地 attachment lifecycle 已由 `TASK-20260517-004` 补齐，Vector Store File Batches 本地 lifecycle 已由 `TASK-20260517-005` 补齐，Vector Store File Content 本地读取基线已由 `TASK-20260518-001` 补齐，Vector Store Search 本地文本检索基线已由 `TASK-20260518-002` 补齐，Responses file_search 本地 Vector Store 绑定已由 `TASK-20260518-003` 补齐。
- `OpenAI-compatible Generic` 已明确不泛化 object lifecycle，这是正确的；OpenAI Direct 也需要同等粒度地声明“已实现/未实现/需真实 smoke”。

## 优先级建议

1. High：先修 Chat/Responses 参数保真与原生 Responses 执行边界。原因是这是客户最容易直接碰到的兼容性问题。
2. High：补齐 OpenAI 官方资源族覆盖声明与缺失资源族任务边界。原因是“完全兼容”争议主要来自资源族缺失。
3. Medium：统一 public OpenAPI、provider catalog、conformance accepted exceptions 和 docs。原因是现有实现和公开说明不一致，会误导客户与测试。

## 生成任务

- `TASK-20260514-013 OpenAI Chat/Responses 参数全量保真与原生 Responses 边界`
- `TASK-20260514-014 OpenAI 官方资源族覆盖差距补齐`
- `TASK-20260514-015 OpenAI 公开 OpenAPI、catalog 与 conformance 事实源校准`

## 验证记录

本轮未改业务代码，未运行全量测试。验证方式为：

- 官方 OpenAI API Reference 在线核对。
- `rg` 检索 `protocol/ingress/openai`、`gateway/core/execution`、`gateway/core/interop`、`provider-catalog.json`、`docs/openapi/public-openapi.json`、conformance tests。
- 静态读取关键文件：`OpenAiChatCompletionRequest`、`OpenAiChatCompletionRequestMapper`、`OpenAiResponsesController`、`OpenAiResponsesRequestMapper`、`OpenAiNativeGatewayChatRuntime`、`GatewayRequestFeatureService`、`AsyncLifecycleGatewayResourceExecutor`、OpenAI resource controllers。

## 最终判断

可以对外宣称：

> 当前项目支持 OpenAI-compatible 核心 Chat/Responses 接入，并已覆盖一批 OpenAI 官方资源生命周期的基础路径。

不应对外宣称：

> 当前项目已经完全实现 OpenAI API，或完全兼容 OpenAI API 所有参数。
