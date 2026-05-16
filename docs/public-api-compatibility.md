# 公开文档、兼容性样例与 i18n 基础

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
- 当前 `sequence_number` 是 gateway 本地编码顺序，不代表 OpenAI 上游原始 SSE 序号；完整 Realtime WebSocket 事件代理仍以 provider capability matrix 和后续 Realtime 任务为准。

## OpenAI Idempotency-Key

非流式 `POST /v1/chat/completions` 与 `POST /v1/responses` 支持 `Idempotency-Key` 本地响应重放。网关按 Distributed Key、request path、`Idempotency-Key` 和 request fingerprint 记录最终 JSON payload；重复 key 且请求体一致时直接返回缓存响应并带 `X-AI-Gateway-Idempotency-Replayed: true`，重复 key 但请求体不同会返回 OpenAI-style `invalid_request_error`。流式请求本轮不做 replay，避免 SSE event framing 与 usage 语义漂移。

幂等记录默认保留 24 小时，并由 scheduled cleanup 默认每小时清理一次。可通过 `gateway.openai.idempotency.retention` 和 `gateway.openai.idempotency.cleanup-fixed-delay` 覆盖，例如 `PT24H`、`PT1H`。

## OpenAI Stored Chat Pagination

`GET /v1/chat/completions` 与 `GET /v1/chat/completions/{completionId}/messages` 使用 OpenAI-compatible list envelope：`object: "list"`、`data`、`has_more`，非空页会返回 `first_id` 与 `last_id`。分页参数为 `after`、`limit` 和 `order`；`limit` 默认 20，合法范围 1 到 100；`order` 仅支持 `asc` 或 `desc`，默认 `asc`。Chat Completion list 额外支持 `model` 与 `metadata[key]` 过滤。

## OpenAI Responses 本地生命周期

Stored Responses 支持 `GET /v1/responses/{responseId}`、`DELETE /v1/responses/{responseId}`、`POST /v1/responses/{responseId}/cancel` 与 `GET /v1/responses/{responseId}/input_items`。

- `cancel` 仅对 `background=true` 且未进入终态的本地 stored Response 生效，成功后返回 `object=response`、`status=cancelled` 并记录 `cancelled_at`。
- `input_items` 从创建 Response 时的原始 `input` 生成 OpenAI-compatible list envelope，支持 `after`、`limit`、`order`，默认 `limit=20`、`order=desc`，`limit` 范围为 1 到 100。
- 本地 lifecycle 继续按 Distributed Key 隔离，不能读取、删除或取消其他 key 的 Response。

## OpenAI Rate Limit Headers

OpenAI path 本地限流命中会返回 HTTP 429，错误体为 `rate_limit_error` / `rate_limit_exceeded`，并写入 `Retry-After`、`x-ratelimit-remaining-requests`、`x-ratelimit-remaining-tokens`、`x-ratelimit-reset-requests` 与 `x-ratelimit-reset-tokens`。当前 header 是退避基线：当 precise limit/remaining 尚未从 governance snapshot 传入时，只公开 remaining/reset，不伪造 `x-ratelimit-limit-*`。

## OpenAI Webhook Signature

OpenAI webhook verifier 按 Standard Webhooks 规范校验 `webhook-id`、`webhook-timestamp` 与 `webhook-signature`。签名内容必须使用原始 request body 拼接为 `webhook-id.webhook-timestamp.raw_body`，签名算法为 HMAC-SHA256，`webhook-signature` 支持 `v1,base64` 和多签名空格分隔。`gateway.openai.webhook.secret` 可配置默认 `whsec_` secret，也可以在后续 endpoint 中按 endpoint secret 显式传入。timestamp tolerance 默认 5 分钟，`webhook-id` replay marker 默认保留 24 小时。

## Codex CLI 示例

```powershell
$env:OPENAI_API_KEY=$env:X_AI_GATEWAY_API_KEY
$env:OPENAI_BASE_URL="https://gateway.example.com/v1"
```

## OpenAPI

- 运行时入口：`GET /public/docs/openapi.json`
- 本地维护文件：[openapi/public-openapi.json](openapi/public-openapi.json)
- 范围：公开 docs、OpenAI-compatible Chat/Responses、Web Search、Claude Messages、Gemini generateContent、Video/Music async task、Media provider matrix。
- 非范围：内部 Admin 全量接口、真实 provider 私有字段、未公开的运营接口。

## i18n 策略

- `zh-CN` 是管理端与 Portal 默认 UI 语言。
- `en-US` 覆盖公开 docs bundle、OpenAPI 描述和 SDK 示例。
- 前端运行时语言切换尚未启用，后续先抽取导航、标题、表格列名、按钮和错误提示。

## 主流 API parity 说明

- OpenAI-compatible 与 xAI/Grok：`/v1/responses` 会保留原始 Responses 字段；Native runtime 已下发 `service_tier`、`parallel_tool_calls`、`prompt_cache_key`、`top_logprobs`、`safety_identifier`、`verbosity` 与 metadata。Responses-only 字段如 `truncation`、`text`、`prompt_cache_retention`、`include`、`previous_response_id` 会进入 provider extra body；Grok prompt cache affinity 使用 `prompt_cache_key` 派生 `x-grok-conv-id`。
- Anthropic Messages：支持 `service_tier`、`container`、`metadata`、`context_management` 与受控 `mcp_servers` 下发。`mcp_servers` 默认需要 `x_ai_gateway_mcp_allowlist` 或 `x_ai_gateway_allow_mcp_servers=true`，并会自动合并 `anthropic-beta: mcp-client-2025-04-04`。
- Gemini generateContent：支持保留 `generationConfig.thinkingConfig`、`toolConfig.functionCallingConfig`、`googleSearch`、`urlContext` 与标准 function declarations。`googleMaps` grounding 默认需要 `x_ai_gateway_allow_google_maps=true`，避免未授权计费或外部访问。

## 错误码说明

- `invalid_api_key`：Distributed Key 无效、过期或未启用。
- `rate_limit_exceeded`：触发 key 或 route policy 限流。
- `no_route_available`：没有可用 provider、site、credential 或模型候选。
- `insufficient_balance`：用户余额或订阅额度不足。

## 当前取舍

本轮提供后端 docs bundle、最小 OpenAPI JSON、本地 Markdown 与主流 API parity 说明，先让公开兼容信息可访问、可测试、可翻译，并把“OpenAI-compatible 声明”和“provider-native 能力”明确拆开。完整 OpenAPI 生成器、前端语言切换组件、真实 provider smoke 和第三方 SDK 全量适配留到后续。
