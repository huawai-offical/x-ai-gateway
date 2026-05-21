# 公开文档、兼容性样例与 i18n 基础

> 当前状态：控制台中的 `能力矩阵`、`Native 命名空间兼容`、`站点档案`、`向量检索排障沙盒` 等入口已下线。本文保留公开协议、兼容性边界和 API 事实源；凡提到 Vector Stores、Responses `file_search`、Codex smoke 或 provider catalog，均应理解为“后端/API 暂保留”，不是当前控制台主入口。

## 文档接口

```text
GET /public/docs/compatibility?locale=zh-CN
GET /public/docs/compatibility?locale=en-US
GET /public/docs/openapi.json
```

接口返回结构化 docs bundle，覆盖：

- quick start 接入步骤。
- OpenAI、Claude、Gemini、Ollama、Rerank、Web Search 兼容性矩阵。
- provider preset 支持矩阵，包括 OpenAI、Azure OpenAI、DeepSeek、Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Dify、OpenRouter、xAI、Perplexity、Cohere、Jina、Together、Fireworks、Mistral、Anthropic、Gemini、Vertex AI。
- curl、OpenAI SDK、Claude Code、Gemini CLI 示例。
- Codex CLI 云端代理接入示例。
- OpenAPI URL、SDK targets 与 i18n policy。
- 错误码、限流、计费、路由、rerank、web_search 和 conformance 说明。
- `zh-CN` 与 `en-US` 双语基础文本。
- OpenAI Chat 参数兼容证明，覆盖 `response_format`、`tools/tool_choice`、`store/metadata`、`modalities/audio`、`web_search_options` 等关键字段。

其中 provider preset 会额外暴露：

- `compatibilitySurface`
- `supportStrategy`
- `modelFamilies`
- `pricingMetadata`
- `unsupportedFeatures`

OpenAI Direct preset 使用 `openai-native` / `native-first` 口径：Chat、Responses、Conversations local lineage、Webhooks ingress event persistence、Files、Uploads、Models list/get、Vector Stores local lifecycle / file attachment / local chunk ingestion / file content read / local text search / file batch、Responses file_search 本地 Vector Store 绑定、Realtime client secret 与 Realtime WebSocket ingress 已分批闭环，但它不再声明官方 API 全量覆盖。`unsupportedFeatures` 会明确列出 Vector Stores real embedding/vector index ingestion/semantic search/hosted file_search_call lifecycle、非核心官方 API out-of-scope、Realtime full calls/WebRTC/SIP/translation/transcription 等边界。OpenAI `/v1/batches`、`/v1/fine_tuning/jobs*`、`DELETE /v1/models/{model}` 的 fine-tuned owner-role delete 语义不属于当前公开功能性服务 API。

Anthropic、Gemini、Vertex 与 Codex 同步按 OpenAI 标准功能区收紧：

- Anthropic 只保留 Messages、tools、thinking 等对话功能区，`message_batches`、provider admin、evals 等官方非核心 API 不纳入兼容面。
- Gemini 只保留 generateContent、embeddings、files 等可映射支撑面；`batchGenerateContent`、tuning、eval-style 和 provider admin API 不纳入兼容面。
- Vertex 只把 project/location 作为 Google Cloud 寻址和凭证边界，功能上仍限定在 Gemini 标准区；pipeline/job/admin、batch prediction、tuning 不纳入兼容面。
- Codex 单独限定为 ChatGPT 官方账号的 Responses smoke/反代边界，不作为通用 provider catalog preset，不把非 Responses 的 Codex 内部 API 暴露为产品面。

## OpenAI-compatible 示例

```powershell
curl https://gateway.example.com/v1/chat/completions `
  -H "Authorization: Bearer $env:X_AI_GATEWAY_API_KEY" `
  -H "Content-Type: application/json" `
  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"ping"}]}'
```

## Web Search 示例

```powershell
curl https://gateway.example.com/v1/web_search `
  -H "Authorization: Bearer $env:X_AI_GATEWAY_API_KEY" `
  -H "Content-Type: application/json" `
  -d '{"model":"sonar-pro","query":"latest provider adapter updates","search_recency_filter":"month"}'
```

`/v1/web_search` 只路由到明确支持的 provider adapter，例如 OpenAI 或 Perplexity；通用 OpenAI-compatible 站点不会自动获得该能力。

## SDK 示例

```javascript
import OpenAI from "openai";

const client = new OpenAI({
  apiKey: process.env.X_AI_GATEWAY_API_KEY,
  baseURL: "https://gateway.example.com/v1",
});
```

## OpenAI Chat 参数兼容证明

参数级事实源位于 [openai-chat-completions-parameter-parity.json](../src/test/resources/conformance/openai-chat-completions-parameter-parity.json)。当前已闭环的 Chat create 参数按三类处理：

- Typed mapping：`response_format`、`modalities`、`audio`、`web_search_options`、`parallel_tool_calls`、`service_tier` 等会进入 OpenAI native typed request 字段。
- Canonical / lifecycle：`store`、`metadata` 会进入 canonical metadata，并在 `store=true` 时形成本地 `chatcmpl_` stored Chat lifecycle。
- Compatibility fallback：deprecated `functions/function_call` 会转换为 `tools/tool_choice`；OpenAI-compatible 站点只在对应 provider 能力允许时保留 raw fallback。

公开 OpenAPI 已给 `/v1/chat/completions` 添加 request body schema，展示 `response_format`、`tools`、`tool_choice`、`store`、`metadata`、`web_search_options`、`modalities`、`audio`、`stream_options`、`prediction`、`prompt_cache_key` 与 `safety_identifier` 等关键字段。完整 OpenAI Direct 行为以 conformance matrix 和单元测试为准；第三方 OpenAI-compatible 站点仍以 provider capability matrix 为准。

Advanced JavaScript 示例位于 [chat-advanced-parameters.mjs](sdk-examples/javascript/chat-advanced-parameters.mjs)。示例默认不启用 `web_search_options` 或 `audio`，需要分别设置 `X_AI_GATEWAY_CHAT_WEB_SEARCH=1`、`X_AI_GATEWAY_CHAT_AUDIO=1` 后才会发送对应高级字段，避免普通 smoke 在弱兼容站点误失败。

## OpenAI Streaming Events

- Chat Completions stream 使用 `chat.completion.chunk`，同一次 stream 内本地编码的 chunk 会保持同一个 `id` 与 `created`。
- 当请求设置 `stream_options.include_usage=true` 时，系统会在 `data: [DONE]` 前输出一个 `choices=[]` 且带 `usage` 的 chunk；如果 canonical completed event 没有 usage，则不会伪造 token 数。
- Responses stream 使用语义化 SSE event，payload 会携带从 0 开始本地递增的 `sequence_number`，用于客户端按序处理 `response.created`、`response.output_text.delta`、`response.completed` 等事件。
- Responses stream delta events 默认包含非空 `obfuscation` 字段；当请求设置 `stream_options.include_obfuscation=false` 时，`response.output_text.delta`、`response.reasoning_summary_text.delta` 与 `response.function_call_arguments.delta` 不输出该字段。
- 当前 `sequence_number` 是 gateway 本地编码顺序，不代表 OpenAI 上游原始 SSE 序号；Realtime WebSocket 入口见下一节，真实上游二进制音频帧透传仍以后续 Realtime smoke 为准。

## OpenAI Realtime WebSocket

`/v1/realtime?model=...` 已提供 OpenAI-compatible WebSocket 入口，面向 server-to-server 或受控客户端代理场景。连接要求：

- 使用 `Authorization: Bearer <x-ai-gateway-key>` 完成 Distributed Key 鉴权。
- `model` query 参数为空时默认使用 `gpt-realtime`。
- 握手成功后创建并 connect `openai_realtime` Live Session，首个 outbound JSON event 为 `session.created`。
- 客户端发送 JSON text events；`type` 会作为 runtime event type，原始 payload 会写入 Live Session 事件流。
- `session.update` 会返回 `session.updated`，非法 JSON、缺失 `type` 或非文本帧会返回 OpenAI-style `error` event。
- `input_audio_buffer.append` 会从 `audio` 或 `delta` base64 字段估算 `audioBytes`，用于本地观测和 conformance。

当前边界：本入口不在本地生成真实模型音频/文本输出，不实现 WebRTC/SIP/Realtime calls，也不直接拨号 OpenAI 上游 WebSocket；真实 provider 网络拨号、二进制帧透传和完整 server event 语义仍需后续任务继续推进。

## OpenAI Idempotency-Key

非流式 `POST /v1/chat/completions` 与 `POST /v1/responses` 支持 `Idempotency-Key` 本地响应重放。网关按 Distributed Key、request path、`Idempotency-Key` 和 request fingerprint 记录最终 JSON payload；重复 key 且请求体一致时直接返回缓存响应并带 `X-AI-Gateway-Idempotency-Replayed: true`，重复 key 但请求体不同会返回 OpenAI-style `invalid_request_error`。流式请求本轮不做 replay，避免 SSE event framing 与 usage 语义漂移。

幂等记录默认保留 24 小时，并由 scheduled cleanup 默认每小时清理一次。可通过 `gateway.openai.idempotency.retention` 和 `gateway.openai.idempotency.cleanup-fixed-delay` 覆盖，例如 `PT24H`、`PT1H`。

## OpenAI Stored Chat Pagination

`GET /v1/chat/completions` 与 `GET /v1/chat/completions/{completionId}/messages` 使用 OpenAI-compatible list envelope：`object: "list"`、`data`、`has_more`，非空页会返回 `first_id` 与 `last_id`。分页参数为 `after`、`limit` 和 `order`；`limit` 默认 20，合法范围 1 到 100；`order` 仅支持 `asc` 或 `desc`，默认 `asc`。Chat Completion list 额外支持 `model` 与 `metadata[key]` 过滤。

Stored Chat Completion list 已使用专用数据库游标查询下推 `distributedKeyId`、`resourceType`、`chatcmpl_` resource key 前缀、`requestModel`、`createdAt/id` cursor 与排序；`metadata[key]` 继续在候选批次上做 JSON 精确过滤，并会跨批次扫描直到凑满当前页或数据库无更多候选，避免固定 scan window 导致窗口外匹配项漏页。

## OpenAI Responses 生命周期

Stored Responses 支持 `GET /v1/responses/{responseId}`、`DELETE /v1/responses/{responseId}`、`POST /v1/responses/{responseId}/cancel` 与 `GET /v1/responses/{responseId}/input_items`。

- `cancel` 仅对 `background=true` 且未进入终态的本地 stored Response 生效，成功后返回 `object=response`、`status=cancelled` 并记录 `cancelled_at`。
- `input_items` 从创建 Response 时的原始 `input` 生成 OpenAI-compatible list envelope，支持 `after`、`limit`、`order`，默认 `limit=20`、`order=desc`，`limit` 范围为 1 到 100。
- OpenAI Direct native create + `store=true` 会为本地 `resp_...` 记录上游 Response id、credential 与 site profile lineage；后续 retrieve/delete/cancel/input_items 会用原凭证同步真实上游对象，再把返回对象 id 重写为本地 id。
- 如果客户端只持有未知远端 `resp_...` id 且本地没有 lineage，retrieve/delete/cancel/input_items 只有在提供 `model` query 或 `X-AI-Gateway-OpenAI-Model` header 时才会走 OpenAI Direct route-hint passthrough；无 hint 时保持本地 not found，不猜测 credential。
- retrieve 与 input_items 已接收 OpenAI Responses `include` query 参数；本地 stored baseline 对 `include` 做 no-op acceptance，带 OpenAI Direct upstream lineage 或显式 route hint 的对象会原样转发 `include` 到上游。
- Responses tools 当前执行 `function` tools；`file_search` 可校验当前 Distributed Key 下的本地 `vector_store_ids`，复用本地 Vector Store Search 结果注入上下文，并移除 hosted tool，避免本地 `vs_...` 透传给上游。`web_search_preview`、`mcp`、`custom`、`computer_use_preview`、`code_interpreter`、`image_generation`、`shell`、`apply_patch` 等其它非 function tools 会返回 OpenAI-style `invalid_request_error`，不会再被静默跳过。详细矩阵见 [openai-responses-tools-compatibility.md](openai-responses-tools-compatibility.md)。
- `POST /v1/responses/input_tokens` 在 OpenAI Direct native route 可用时优先转发到上游并保留上游 HTTP 状态；route 不可用或不是 OpenAI Direct 时返回本地 deterministic token estimate。只有本地 estimate 用于兼容和预估，不作为 OpenAI 官方 tokenizer 或账单精确依据。
- `POST /v1/responses/compact` 在 OpenAI Direct native route 可用时优先转发到上游并保留上游 HTTP 状态；route 不可用或不是 OpenAI Direct 时返回本地 emulation，使用 opaque compaction marker 表示本地兼容结果。本地 marker 不等价于 OpenAI 官方 encrypted compaction item，不作为真实模型压缩结果。
- 本地 lifecycle 继续按 Distributed Key 隔离，不能读取、删除或取消其他 key 的 Response。

## OpenAI Conversations 生命周期

Conversations 采用 gateway local lineage，面向 OpenAI-compatible 客户端提供本地对象生命周期：

- `POST /v1/conversations`：创建 `conv_...` 本地 conversation，支持 optional `metadata` 与最多 20 条初始 `items`。
- `GET /v1/conversations/{conversationId}`、`POST /v1/conversations/{conversationId}`、`DELETE /v1/conversations/{conversationId}`：分别用于读取、更新 metadata 和软删除 conversation；删除返回 `object=conversation.deleted`。
- `POST /v1/conversations/{conversationId}/items`：一次最多追加 20 条 item，返回 OpenAI-compatible list envelope。
- `GET /v1/conversations/{conversationId}/items`：支持 `after`、`include`、`limit`、`order`，默认 `limit=20`、`order=desc`，`limit` 范围 1 到 100。
- `GET /v1/conversations/{conversationId}/items/{itemId}` 与 `DELETE /v1/conversations/{conversationId}/items/{itemId}`：按当前 Distributed Key 与 parent conversation 双重校验，避免跨租户或跨 conversation 读取。

本地 item 作为独立 `gateway_async_resource` 保存，`upstreamObjectId` 记录 parent conversation id。删除 conversation 不级联删除 item lineage；但公开 item endpoint 仍要求 conversation id 可定位。本轮不声明 OpenAI Direct Conversations 上游 passthrough，`include` 作为 no-op 兼容参数被接受。

## OpenAI Vector Stores 本地 Lifecycle

控制台中的向量页面和排障沙盒已下线；以下内容仅描述仍暂时保留的公开 `/v1/vector_stores*` 与 Responses `file_search` 本地绑定事实。

Vector Stores 先建立 gateway local lifecycle 基线，面向 OpenAI-compatible 客户端提供本地对象生命周期：

- `POST /v1/vector_stores`：创建 `vs_...` 本地 vector store，支持 optional `name`、`metadata`、`file_ids`、`expires_after` 与 `expires_at`。
- `GET /v1/vector_stores`：返回 OpenAI-compatible list envelope，支持 `after`、`limit`、`order`，默认 `limit=20`、`order=desc`，`limit` 范围 1 到 100。
- `GET /v1/vector_stores/{vectorStoreId}`、`POST /v1/vector_stores/{vectorStoreId}`、`DELETE /v1/vector_stores/{vectorStoreId}`：分别用于读取、更新和软删除当前 Distributed Key 下的本地 vector store；删除返回 `object=vector_store.deleted`。
- `POST /v1/vector_stores/{vectorStoreId}/files`：把 `file_id` 作为本地 attachment 关联到 vector store，返回 `object=vector_store.file`；支持 `attributes` 与 `chunking_strategy`，创建时读取当前 Distributed Key 下的 gateway file，写入本地 chunk ingestion metadata，并把 `usage_bytes` 设置为真实文件字节数。
- `GET /v1/vector_stores/{vectorStoreId}/files`：返回当前 vector store 的本地 attachment list envelope，支持 `after`、`limit`、`order`、`filter`。
- `GET /v1/vector_stores/{vectorStoreId}/files/{fileId}` 与 `DELETE /v1/vector_stores/{vectorStoreId}/files/{fileId}`：按当前 Distributed Key 与 parent vector store 双重校验；删除返回 `object=vector_store.file.deleted`，并同步更新 parent `file_counts`。
- `GET /v1/vector_stores/{vectorStoreId}/files/{fileId}/content`：读取当前 Distributed Key 下指定 attachment 对应的 gateway file，本地返回 `object=vector_store.file_content.page`、`data/content` 文本页、`has_more=false` 与 `next_page=null`；该能力不等价于 OpenAI 托管解析、embedding 或真实向量索引。
- `POST /v1/vector_stores/{vectorStoreId}/search`：优先检索 attachment metadata 中已固化的本地 ingestion chunks；历史 attachment 没有 ingestion metadata 时回退读取原始 gateway file 文本。返回 `object=vector_store.search_results.page`、`search_query`、`data[].file_id`、`score`、`attributes` 与文本片段；支持 `query`、attributes `filters`、`max_num_results` 和 `ranking_options.score_threshold`，但不等价于 OpenAI 托管 semantic vector retrieval、rerank 或 query rewrite。
- `POST /v1/vector_stores/{vectorStoreId}/file_batches`：批量创建本地 `vector_store.file_batch`，支持 `file_ids` 或 `files` 两种输入；会先完成非空、去重和已存在 attachment 校验，再一次性创建 batch 与 file attachment，并为每个 attachment 固化本地 chunk ingestion metadata。
- `GET /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}`：读取当前 Distributed Key 和 parent vector store 下的本地 file batch。
- `POST /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/cancel`：本地同步完成的 batch 返回清晰错误，不把已创建 attachment 伪装成可取消；后续真实异步 ingestion 接入后再扩展 `in_progress` 状态迁移。
- `GET /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files`：按 batch metadata 记录的 `file_ids` 返回 active attachment list envelope，支持 `after`、`limit`、`order`、`filter`。

当前实现只声明 vector store lifecycle、本地 file attachment lifecycle、本地 chunk ingestion metadata、本地 file content read、本地文本 search、本地 file batch lifecycle 与 Responses `file_search` 本地 Vector Store 绑定。本地 ingestion metadata 使用字符近似 token 切 chunk，记录 `content_sha256`、`chunk_count` 与 chunk 文本，仍不等价于 OpenAI 托管 embedding、真实向量索引或语义检索；真实 embedding/vector index ingestion、语义检索、hosted `file_search_call` lifecycle 和 OpenAI Direct 上游同步仍归属 `TASK-20260514-023` 后续切片。

## OpenAI Rate Limit Headers

OpenAI path 本地限流命中会返回 HTTP 429，错误体为 `rate_limit_error` / `rate_limit_exceeded`，并写入 `Retry-After`、`x-ratelimit-remaining-requests`、`x-ratelimit-remaining-tokens`、`x-ratelimit-reset-requests` 与 `x-ratelimit-reset-tokens`。当前 header 是退避基线：当 precise limit/remaining 尚未从 governance snapshot 传入时，只公开 remaining/reset，不伪造 `x-ratelimit-limit-*`。

## OpenAI Webhook Signature

OpenAI webhook verifier 按 Standard Webhooks 规范校验 `webhook-id`、`webhook-timestamp` 与 `webhook-signature`。签名内容必须使用原始 request body 拼接为 `webhook-id.webhook-timestamp.raw_body`，签名算法为 HMAC-SHA256，`webhook-signature` 支持 `v1,base64` 和多签名空格分隔。`gateway.openai.webhook.secret` 可配置默认 `whsec_` secret，也可以在后续 endpoint 中按 endpoint secret 显式传入。timestamp tolerance 默认 5 分钟，`webhook-id` replay marker 默认保留 24 小时。

`POST /v1/webhooks/openai` 已作为 OpenAI Webhooks 接收入口。Controller 以 raw body 完成验签，合法 event 会保存为 `gateway_async_resource` 的 `WEBHOOK_EVENT` 记录，`resourceKey` 优先使用 event `id`，`metadata_json` 记录 `webhook_id`、`webhook_timestamp`、`event_type`、`source=openai` 与 `received_at`。同一 `webhook-id` 重复投递或同一 event id 以新 delivery 再次到达时，接口返回 `received=true`、`duplicate=true`，不重复落库。

## Codex CLI 示例

```powershell
$env:OPENAI_API_KEY=$env:X_AI_GATEWAY_API_KEY
$env:OPENAI_BASE_URL="https://gateway.example.com/v1"
```

## OpenAPI

- 运行时入口：`GET /public/docs/openapi.json`
- 本地维护文件：[openapi/public-openapi.json](openapi/public-openapi.json)
- 范围：公开 docs、OpenAI-compatible Chat/Responses/Conversations/Vector Stores/Webhooks、Web Search、Claude Messages、Gemini/Vertex generateContent 标准区、Codex Responses smoke 边界、Video/Music async task、Media provider matrix。
- 非范围：内部 Admin 全量接口、真实 provider 私有字段、未公开的运营接口。

## i18n 策略

- `zh-CN` 是管理端与 Portal 默认 UI 语言。
- `en-US` 覆盖公开 docs bundle、OpenAPI 描述和 SDK 示例。
- 前端运行时语言切换尚未启用，后续先抽取导航、标题、表格列名、按钮和错误提示。

## 主流 API parity 说明

- OpenAI-compatible 与 xAI/Grok：`/v1/responses` 会保留原始 Responses 字段；Native runtime 已下发 `service_tier`、`parallel_tool_calls`、`prompt_cache_key`、`top_logprobs`、`safety_identifier`、`verbosity` 与 metadata。Responses-only 字段如 `truncation`、`text`、`prompt_cache_retention`、`include`、`previous_response_id` 会进入 provider extra body；Grok prompt cache affinity 使用 `prompt_cache_key` 派生 `x-grok-conv-id`。
- OpenAI Direct 非流式 Responses create 优先返回上游原始 Responses JSON，并将 `model` 重写为 public model；OpenAI Direct `stream=true` 会透明转发上游原始 SSE 事件，保留 upstream event name、data、sequence 与未知字段；没有 native raw 能力的本地/兼容路径继续使用 canonical Responses encoder。
- 带 OpenAI Direct upstream lineage 的 stored Response 支持远端 retrieve/delete/cancel/input_items passthrough；任意未知远端 `resp_...` id 不做无模型盲路由。
- OpenAI Direct `responses/input_tokens` 支持 native passthrough；上游已执行后的错误状态不回退成本地估算，避免掩盖真实请求错误。
- Anthropic Messages：支持 `service_tier`、`container`、`metadata`、`context_management` 与受控 `mcp_servers` 下发。`mcp_servers` 默认需要 `x_ai_gateway_mcp_allowlist` 或 `x_ai_gateway_allow_mcp_servers=true`，并会自动合并 `anthropic-beta: mcp-client-2025-04-04`。
- Gemini generateContent：支持保留 `generationConfig.thinkingConfig`、`toolConfig.functionCallingConfig`、`googleSearch`、`urlContext` 与标准 function declarations。`googleMaps` grounding 默认需要 `x_ai_gateway_allow_google_maps=true`，避免未授权计费或外部访问。
- Vertex：仅在 provider catalog 和 compatibility matrix 中声明 project/location 寻址、凭证与 Gemini 标准区映射，不把 Vertex AI Platform 的 pipeline/job/admin/tuning/batch prediction 扩展为公开 API。
- Codex：官方账号 smoke 只调用 `/backend-api/codex/responses`，并沿用 Responses request body、streaming、reasoning effort 和 usage budget guard；其它 Codex 内部接口不进入产品兼容面。

## 错误码说明

- `invalid_api_key`：Distributed Key 无效、过期或未启用。
- `rate_limit_exceeded`：触发 key 或 route policy 限流。
- `no_route_available`：没有可用 provider、site、credential 或模型候选。
- `insufficient_balance`：用户余额或订阅额度不足。

## 当前取舍

本轮提供后端 docs bundle、最小 OpenAPI JSON、本地 Markdown 与主流 API parity 说明，先让公开兼容信息可访问、可测试、可翻译，并把“OpenAI-compatible 声明”和“provider-native 能力”明确拆开。完整 OpenAPI 生成器、前端语言切换组件、真实 provider smoke 和第三方 SDK 全量适配留到后续。
