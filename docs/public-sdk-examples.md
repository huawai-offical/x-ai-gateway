# Public OpenAPI 与 SDK 示例

关联需求：[REQ-20260506-019 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取](requirements/REQ-20260506-019-openapi-sdk-frontend-i18n.md)  
关联任务：[TASK-20260506-023 OpenAPI 自动生成、SDK 示例与前端 i18n 抽取](../tasks/done/TASK-20260506-023-openapi-sdk-frontend-i18n.md)

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

示例默认读取：

- `X_AI_GATEWAY_BASE_URL`
- `X_AI_GATEWAY_API_KEY`
- `X_AI_GATEWAY_MODEL`

Advanced JavaScript 示例额外展示 `response_format`、`tools/tool_choice`、`store/metadata`、`parallel_tool_calls`、`service_tier`、`stream_options`，并通过 `X_AI_GATEWAY_CHAT_WEB_SEARCH=1` 与 `X_AI_GATEWAY_CHAT_AUDIO=1` 显式启用 `web_search_options` 和 `modalities/audio`。

没有真实 provider key 时，示例用于展示接入形态和 SDK 代码，不承诺上游真实调用成功。
