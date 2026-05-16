# REP-20260514 主流厂商 API/changelog 复核

## 背景

本报告承接 [REQ-20260514-006](../requirements/REQ-20260514-006-community-home-api-docs-refresh.md) 与 [TASK-20260514-008](../../tasks/done/TASK-20260514-008-mainstream-api-docs-changelog-refresh.md)，只以官方 API 文档、官方 changelog 或官方迁移说明为事实源。

## 结论

当前项目的主链路仍然成立：OpenAI-compatible Chat、Responses、Anthropic Messages、Gemini generateContent、Perplexity web search adapter 与 xAI OpenAI-compatible 面都已覆盖核心请求。主流厂商在 2025-2026 继续把 reasoning、service tier、server-side tools、MCP、search filters、prompt cache 与多模态输出参数前移到一等参数；本轮已闭环 OpenAI/xAI Responses、Anthropic MCP/service tier/container/context management、Gemini thinking/toolConfig/grounding 三个 High 差距。

## 官方来源

- OpenAI Responses API：<https://platform.openai.com/docs/api-reference/responses/create>
- Anthropic Messages API：<https://docs.anthropic.com/en/api/messages>
- Anthropic API release notes：<https://docs.anthropic.com/en/release-notes/api>
- Google Gemini generateContent：<https://ai.google.dev/api/generate-content>
- Google Gemini release notes：<https://ai.google.dev/gemini-api/docs/changelog>
- xAI Chat/Responses API：<https://docs.x.ai/docs/api-reference>
- xAI 2026-05-15 model retirement：<https://docs.x.ai/developers/migration/may-15-retirement>
- xAI prompt caching：<https://docs.x.ai/developers/advanced-api-usage/prompt-caching>
- Perplexity Sonar API：<https://docs.perplexity.ai/api-reference/chat-completions-post>
- Perplexity changelog：<https://docs.perplexity.ai/changelog/changelog>

## 对比明细

| 厂商 | 官方变化/重点 | 当前项目状态 | 本轮处理 |
| --- | --- | --- | --- |
| OpenAI | Responses API 持续强化 `parallel_tool_calls`、`service_tier`、`reasoning`、`prompt_cache_key`、server-side tools 与 streaming events。 | `/v1/responses` 已有 mapper、Codex metadata、SSE encoder 和 full body providerExtensions；Native runtime 已补 Chat SDK 可承接字段与 Responses-only extra body 保真。 | TASK-20260514-009 已闭环。 |
| Anthropic | Messages API 使用官方 snake_case 字段；release notes 增加 extended thinking、Files API、MCP connector、service tier、search result blocks 等。 | Messages 入口已有 text/image/document/tool/stream/cache usage；Native runtime 已补 `tool_choice`、thinking budget、`service_tier`、`container`、`metadata`、`context_management` 与受控 `mcp_servers`。 | TASK-20260514-010 已闭环。 |
| Google Gemini | `generateContent` 的 `generationConfig` 明确包含 `responseModalities`，release notes 追加 multi-tool、URL context、thinking、Live/Interactions 等能力。 | generateContent、stream、fileData、functionDeclarations、image/audio resource mode 已有；Native runtime 已通过 SDK `fromJson` 保留 `thinkingConfig`、`toolConfig`、Google Search grounding 与 URL context。 | TASK-20260514-011 已闭环。 |
| xAI | 官方 Chat/Responses API 提供 `/v1/responses`、`service_tier`、`parallel_tool_calls`、tools、prompt cache；2026-05-15 12:00 PT 起多个 Grok slug 将重定向到 `grok-4.3` 或 image 替代模型。 | provider catalog 已有 xAI preset 与 `grok-4.3`；Native runtime 已把 `prompt_cache_key` 映射到 Grok `x-grok-conv-id`。 | TASK-20260514-009 已闭环。 |
| Perplexity | Sonar API 增加 `web_search_options`、date filters、image filters、`stream_mode`、`reasoning_effort`、`language_preference`，changelog 强调 reasoning effort。 | `/v1/web_search` 到 `/v1/sonar` adapter 已有，但透传字段少于官方最新字段。 | 已补 Perplexity Sonar 低风险 passthrough 字段与回归测试。 |

## 已完成更新

- `PerplexityWebSearchAdapter` 扩展 Sonar passthrough 字段：`web_search_options`、日期过滤、图片过滤、`stream_mode`、`reasoning_effort`、`language_preference` 等。
- `AnthropicMessagesRequest` 显式支持官方 snake_case：`max_tokens`、`tool_choice`、`input_schema`，并保留 `thinking`、`service_tier`、`metadata`、`container`、`context_management`、`mcp_servers` 等扩展字段。
- `AnthropicMessagesRequestMapper` 将 `thinking` 纳入 canonical reasoning；`AnthropicNativeGatewayChatRuntime` 下发 `tool_choice` 与 `thinking.budget_tokens`。
- `provider-catalog.json` 的 xAI preset 增加 `grok-4.3`，并记录 2026-05-15 退役 slug 自动重定向风险。
- OpenAI/xAI Native runtime 保留 Responses parity 字段，Grok prompt cache affinity 使用 `x-grok-conv-id`。
- Anthropic MCP/service tier/container/context management 字段已受控下发，MCP connector 需要 allowlist 或显式开关。
- Gemini Native runtime 已保留 `thinkingConfig`、`toolConfig.functionCallingConfig`、`googleSearch` 与 `urlContext`，`googleMaps` 需要显式允许。

## 本轮闭环任务

- [TASK-20260514-009](../../tasks/done/TASK-20260514-009-openai-xai-responses-field-parity.md)：OpenAI/xAI Responses 字段 parity 与 prompt cache/header 保真。
- [TASK-20260514-010](../../tasks/done/TASK-20260514-010-anthropic-mcp-service-tier-field-parity.md)：Anthropic MCP/service tier/container/context management 字段下发与 beta header 治理。
- [TASK-20260514-011](../../tasks/done/TASK-20260514-011-gemini-thinking-toolconfig-grounding-parity.md)：Gemini thinkingConfig/toolConfig/URL context/Grounding 参数 parity。
