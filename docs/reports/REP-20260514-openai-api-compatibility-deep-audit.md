# REP-20260514 OpenAI API 完整兼容性深度审计

## 结论

当前项目不能判定为“OpenAI API 已完全实现”，也不能判定为“OpenAI API 参数完全兼容”。

更准确的状态是：项目已经实现了 OpenAI-style 核心接入面，覆盖 `/v1/chat/completions`、`/v1/responses`、Embeddings、Audio、Images、Files、Uploads、Batches、Fine-tuning、Moderations、Models 和 Realtime client secrets 的一部分；但 Chat/Responses 参数仍是选择性映射或受控保真，Responses 仍主要经由 Chat runtime 执行，官方资源族中 Vector Stores、Evals、Containers、Administration、Chat Completions 对象生命周期、Conversations、Realtime calls、Audio voice consents、Video 官方面等未形成完整公开入口或兼容声明。

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
| Responses | 已有入口、store/get/delete、本地编码和 streaming | `OpenAiResponsesController` 仅公开 create/get/delete；`OpenAiResponsesRequestMapper` 仅把 function tools 转 canonical，非 function tool 被跳过；原 body 保留在 `providerExtensions` | 部分兼容，非原生 Responses 全语义 |
| OpenAI native runtime | 使用 Spring AI `OpenAiApi.ChatCompletionRequest` 执行 | `OpenAiNativeGatewayChatRuntime` 对 Responses 请求仍构造 ChatCompletionRequest；`responsesExtraBody` 只在 `ProviderType.OPENAI_COMPATIBLE` 时保留部分字段 | OpenAI Direct Responses 字段保真不足 |
| Embeddings | JSON passthrough/resource execution | `/v1/embeddings` 在 feature service 与 conformance matrix 中覆盖 | 基础兼容，需真实 smoke 与参数回归 |
| Audio | transcriptions/translations/speech | multipart transcription 只接收 `file/model/language/prompt/response_format/temperature`；speech JSON passthrough | 部分兼容，multipart 参数不全 |
| Images | generations/edits/variations | generations JSON passthrough；edits/variations multipart 只接收有限字段 | 部分兼容，image edit/variation 参数不全 |
| Files | list/create/get/content/delete | list 使用本地 catalog；create 支持 `file/purpose` | 基础生命周期可用，但 list filter/order/after 等官方行为不完整 |
| Uploads | create/get/parts/complete/cancel | 本地多了 GET upload；官方导航只列 create/cancel/complete/parts create | 接近但需对齐官方路径与响应语义 |
| Batches | create/get/cancel | conformance accepted exception 明确未暴露 `GET /v1/batches` | 缺 Batch list |
| Fine-tuning | create/list/get/cancel | accepted exception 明确未暴露 events/checkpoints；controller 未包含 pause/resume/permissions | 缺 events/checkpoints/pause/resume 等 |
| Models | list/get | controller 无 `DELETE /v1/models/{model}` | 缺 fine-tuned model delete |
| Realtime | client secrets | 只公开 `/v1/realtime/client_secrets`；官方还有 translations client secrets、calls、events | 部分兼容 |
| Vector Stores | 未发现实现 | `rg vector_stores` 仅命中文档外无入口 | 缺失 |
| Evals | 未发现实现 | 官方资源族存在，本地无公开入口 | 缺失 |
| Containers | 未发现实现 | 官方资源族存在，本地无公开入口 | 缺失 |
| Administration | 未实现 OpenAI Admin API 兼容面 | 本地 admin 是自身管理后台，不是 OpenAI Admin API | 缺失 |
| Public OpenAPI | 公开文档只列 Chat/Responses/Web Search/Messages/Gemini/Media | `docs/openapi/public-openapi.json` 未列已实现的 Embeddings、Audio、Images、Files、Uploads、Batches、Fine-tuning、Moderations、Models、Realtime | 文档事实源不足 |
| Provider catalog | OpenAI preset 声称基础面，conformance 标记 `responses.emulated` | `provider-catalog.json` 未把 unsupported features 列清楚 | 需要声明校准 |

## 参数兼容性重点缺口

### Chat Completions

本地 `OpenAiChatCompletionRequest` 是强 DTO，未使用 raw JSON 全量保真。当前会直接丢失或无法结构化处理一批官方字段，包括但不限于：

- `max_completion_tokens`、`metadata`、`modalities`、`n`、`prediction`、`presence_penalty`、`prompt_cache_key`、`prompt_cache_retention`、`response_format`、`safety_identifier`、`seed`、`service_tier`、`stop`、`stream_options`、`store`、`top_logprobs`、`top_p`、`user`、`verbosity`、`web_search_options`。
- `tools` 目前 DTO 支持 function tool；官方 Chat 文档已出现 custom tool/grammar 等扩展形态，当前 mapper 不具备全量工具语义。
- `max_tokens` 仍存在，但官方文档已标注其被 `max_completion_tokens` 取代，且对 o-series 不兼容；项目当前仍主要映射 `max_tokens`。

### Responses

本地 Responses 的优势是保留了原始请求体到 `providerExtensions`，但真实执行路径仍不等于官方 Responses API：

- `OpenAiResponsesRequestMapper` 只把 `type=function` 的 tools 转为 canonical tools，`file_search`、`web_search_preview`、`computer_use_preview`、`code_interpreter`、`image_generation`、`mcp`、`custom` 等不会进入 canonical tools。
- `OpenAiNativeGatewayChatRuntime` 仍调用 Chat Completions SDK；`background`、`conversation`、`max_tool_calls`、`prompt`、`store`、`stream_options.include_obfuscation`、`include`、`previous_response_id`、`text`、`truncation` 等只部分保真，且部分 extra body 对 OpenAI Direct 不生效。
- 本地只公开 `GET /v1/responses/{responseId}` 和 `DELETE /v1/responses/{responseId}`，缺官方 `list input items`、`count input tokens`、`cancel`、`compact`、Conversations 及 Items 子资源。

## 公开声明风险

- `docs/openapi/public-openapi.json` 比实际代码窄，容易让客户以为只有 Chat/Responses；但 provider catalog 又说 OpenAI 覆盖多个资源族，二者不一致。
- `provider-catalog.json` 的 OpenAI preset `unsupportedFeatures` 为空，但实际仍缺 Vector Stores、Evals、Containers、Admin、Batch list、Fine-tuning events/checkpoints、Models delete 等。
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

