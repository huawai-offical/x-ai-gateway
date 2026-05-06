# x-ai-gateway

`x-ai-gateway` 是一个多协议、多厂商的 AI 网关，基于 `Spring Boot`、`Spring AI`、`PostgreSQL`、`Redis` 和 `React` 控制台实现，统一承载 OpenAI / Anthropic / Gemini 等协议入口、路由与能力判定、缓存与亲和、usage / audit / observability，以及后台管理能力。

项目当前默认数据库名为 `x_ai_gateway`。

## 本地事实源

本仓库默认使用本地优先协作流程，不再把线上 Notion 或线上 Linear 作为默认事实源。

- 文档索引：[docs/index.md](docs/index.md)
- 任务索引：[tasks/index.md](tasks/index.md)
- 本地协作规则：[AGENTS.md](AGENTS.md)
- 公开兼容文档：[docs/public-api-compatibility.md](docs/public-api-compatibility.md)

## 公开文档 API

运行服务后可读取结构化公开文档：

```text
GET /public/docs/compatibility?locale=zh-CN
GET /public/docs/compatibility?locale=en-US
GET /public/docs/openapi.json
```

公开 docs bundle 覆盖 quick start、provider preset、CLI 接入、SDK 示例、错误码、路由、计费和 conformance。OpenAPI JSON 维护公开接入面的最小事实源。

## 开发约定

- 新需求开始编码前，先在 `docs/requirements/` 和 `tasks/` 中建立本地记录。
- 交付完成后，回写实现结果、验证情况、遗留问题和后续建议。
- 默认不调用线上 Notion/Linear；只有用户明确要求时才使用线上连接器。
