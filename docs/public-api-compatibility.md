# 公开文档、兼容性样例与 i18n 基础

## 文档接口

```text
GET /public/docs/compatibility?locale=zh-CN
GET /public/docs/compatibility?locale=en-US
GET /public/docs/openapi.json
```

接口返回结构化 docs bundle，覆盖：

- quick start 接入步骤。
- OpenAI、Claude、Gemini、Ollama 兼容性矩阵。
- provider preset 支持矩阵，包括 OpenAI、Azure OpenAI、DeepSeek、Qwen、Moonshot、SiliconFlow、Volcengine、MiniMax、Dify、OpenRouter、Cohere、Jina、Together、Fireworks、Mistral、Anthropic、Gemini。
- curl、OpenAI SDK、Claude Code、Gemini CLI 示例。
- Codex CLI 云端代理接入示例。
- OpenAPI URL、SDK targets 与 i18n policy。
- 错误码、限流、计费、路由、rerank 和 conformance 说明。
- `zh-CN` 与 `en-US` 双语基础文本。

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

## SDK 示例

```javascript
import OpenAI from "openai";

const client = new OpenAI({
  apiKey: process.env.X_AI_GATEWAY_API_KEY,
  baseURL: "https://gateway.example.com/v1",
});
```

## Codex CLI 示例

```powershell
$env:OPENAI_API_KEY=$env:X_AI_GATEWAY_API_KEY
$env:OPENAI_BASE_URL="https://gateway.example.com/v1"
```

## OpenAPI

- 运行时入口：`GET /public/docs/openapi.json`
- 本地维护文件：[openapi/public-openapi.json](openapi/public-openapi.json)
- 范围：公开 docs、OpenAI-compatible Chat/Responses、Claude Messages、Gemini generateContent、Video/Music async task、Media provider matrix。
- 非范围：内部 Admin 全量接口、真实 provider 私有字段、未公开的运营接口。

## i18n 策略

- `zh-CN` 是管理端与 Portal 默认 UI 语言。
- `en-US` 覆盖公开 docs bundle、OpenAPI 描述和 SDK 示例。
- 前端运行时语言切换尚未启用，后续先抽取导航、标题、表格列名、按钮和错误提示。

## 错误码说明

- `invalid_api_key`：Distributed Key 无效、过期或未启用。
- `rate_limit_exceeded`：触发 key 或 route policy 限流。
- `no_route_available`：没有可用 provider、site、credential 或模型候选。
- `insufficient_balance`：用户余额或订阅额度不足。

## 当前取舍

本轮提供后端 docs bundle、最小 OpenAPI JSON 和本地 Markdown，先让公开兼容信息可访问、可测试、可翻译，并把“OpenAI-compatible 声明”和“provider-native 能力”明确拆开。完整 OpenAPI 生成器、前端语言切换组件、真实 provider smoke 和第三方 SDK 全量适配留到后续。
