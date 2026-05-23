# Public OpenAPI 与 SDK 示例

关联需求：[REQ-20260506-019 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取](requirements/REQ-20260506-019-openapi-sdk-frontend-i18n.md)
关联任务：[TASK-20260506-023 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取](../tasks/done/TASK-20260506-023-openapi-sdk-frontend-i18n.md)

> 当前状态：控制台中的 `官方账号运行态`、`站点档案`、`向量检索排障沙盒` 等入口已下线。本文继续保留公开 SDK 与 API 示例，描述的是仍暂时保留的接入协议和后端能力，不代表这些能力仍有对应控制台主页面。

## OpenAPI 快照

公开 OpenAPI 事实源位于 [public-openapi.json](openapi/public-openapi.json)。测试会校验：

- `openapi` 版本存在。
- `info.title`、`info.version` 存在。
- 公开 paths 包含 `/v1/chat/completions`、`/v1/responses`、`/public/docs/openapi.json`。
- `/v1/chat/completions` request body schema 包含 `response_format`、`tools`、`tool_choice`、`store`、`metadata`、`web_search_options` 等关键字段。
- bearer auth security scheme 存在。

## SDK 示例

示例索引位于 [sdk-examples/index.json](sdk-examples/index.json)。

| 语言 | 示例 |
| --- | --- |
| Python | [chat_completions.py](sdk-examples/python/chat_completions.py) |
| JavaScript | [chat-completions.mjs](sdk-examples/javascript/chat-completions.mjs) |
| JavaScript advanced | [chat-advanced-parameters.mjs](sdk-examples/javascript/chat-advanced-parameters.mjs) |
| Go | [chat_completions.go](sdk-examples/go/chat_completions.go) |
| Java | [ChatCompletionsExample.java](sdk-examples/java/ChatCompletionsExample.java) |

## 三种接入模式

### OpenAI Direct native

用于接入 OpenAI Direct/native route，适合 Responses、Conversations、file_search、本地 Vector Store 绑定、Files/Uploads 等对话支撑能力。该模式要求路由命中 OpenAI Direct/native provider，并使用 Distributed Key 认证；它描述的是协议接入方式，不表示控制台仍提供官方账号运行态或向量调试主入口。

```python
from openai import OpenAI

client = OpenAI(
    base_url="https://gateway.example.com/v1",
    api_key="dk-xxxxxx",
)

response = client.responses.create(
    model="gpt-4.1-mini",
    input="用三点总结今天的待办。",
    tools=[{
        "type": "file_search",
        "vector_store_ids": ["vs_local_docs"],
    }],
)

print(response.output_text)
```

### OpenAI-compatible Generic

用于 MiMo 等兼容 OpenAI Chat 格式的 provider key，chat、streaming、function tools 与 file/uploads 分开建模；Files/Uploads 只有在 capability snapshot 明示支持时才通过 gateway orchestration 开放，不把 chat 兼容性外推为 Realtime、Batches 或完整 object lifecycle。

```javascript
import OpenAI from "openai";

const client = new OpenAI({
  baseURL: process.env.X_AI_GATEWAY_BASE_URL ?? "https://gateway.example.com/v1",
  apiKey: process.env.X_AI_GATEWAY_API_KEY ?? "dk-xxxxxx",
});

const completion = await client.chat.completions.create({
  model: process.env.X_AI_GATEWAY_MODEL ?? "mimo-chat",
  messages: [{ role: "user", content: "生成一个包含 tools 调用的简短示例。" }],
  tools: [{
    type: "function",
    function: {
      name: "lookup_order",
      description: "查询订单状态",
      parameters: {
        type: "object",
        properties: { order_id: { type: "string" } },
        required: ["order_id"],
      },
    },
  }],
  stream: true,
});

for await (const chunk of completion) {
  process.stdout.write(chunk.choices?.[0]?.delta?.content ?? "");
}
```

### 自定义 provider adapter

用于直接暴露 Anthropic native、Gemini native、Vertex/Gemini compatible 等 provider 原生对话面。它们仍按本项目功能性服务 API 收紧：只保留可映射到对话、streaming、tools、多模态和必要支撑能力的入口。

```bash
curl -sS https://gateway.example.com/v1beta/models/gemini-2.5-flash:generateContent \
  -H "Authorization: Bearer dk-xxxxxx" \
  -H "Content-Type: application/json" \
  -d '{
    "contents": [
      { "role": "user", "parts": [{ "text": "用中文解释这个 API 的工具调用边界。" }] }
    ],
    "tools": [
      { "functionDeclarations": [{ "name": "lookup_doc", "parameters": { "type": "object" } }] }
    ]
  }'
```

示例默认读取：

- `X_AI_GATEWAY_BASE_URL`
- `X_AI_GATEWAY_API_KEY`
- `X_AI_GATEWAY_MODEL`

Advanced JavaScript 示例额外展示 `response_format`、`tools/tool_choice`、`store/metadata`、`parallel_tool_calls`、`service_tier`、`stream_options`，并通过 `X_AI_GATEWAY_CHAT_WEB_SEARCH=1` 与 `X_AI_GATEWAY_CHAT_AUDIO=1` 显式启用 `web_search_options` 和 `modalities/audio`。

没有真实 provider key 时，示例用于展示接入形态和 SDK 代码，不承诺上游真实调用成功。

## Codex CLI 接入配置 (~/.codex/config.toml)

当您使用本地的 Codex CLI 工具通过网关代理进行访问时，可以通过配置 `~/.codex/config.toml` 将通信协议配置为 `"responses"` 模式，以在网关的标准 Responses 边界内建立安全的运行状态。

配置实例如下：

```toml
# ~/.codex/config.toml

[gateway]
# 配置网关 Base URL 路径，指向网关 v1 服务端点
base_url = "https://gateway.example.com/v1"

# 配置为 responses 协议，以便无缝对接网关 Responses 专有接口
wire_api = "responses"

[auth]
# 您的 Distributed Key
api_key = "dk-xxxxxx"
```

通过这一配置，Codex CLI 将把请求路由到 `/backend-api/codex/responses` 私有端点，并支持会话亲和性与基于会话的上下文链追溯。这里的说明只覆盖协议接入，不恢复 `Native 命名空间兼容` 一类控制台入口。
