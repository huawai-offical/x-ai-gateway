# TASK-20260514-008 主流厂商 API/changelog 复核与参数差距闭环

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-006](TASK-20260514-006-community-home-api-docs-refresh.md)  
上游来源：[REQ-20260514-006](../../docs/requirements/REQ-20260514-006-community-home-api-docs-refresh.md)

## 目标

- 阅读主流厂商官方 API 文档与 changelog。
- 对比当前项目的参数、请求映射、Provider Catalog、公开文档和兼容层。
- 低风险差距直接更新，较大改动拆成后续任务。

## 输入

- 官方文档/changelog：OpenAI、Anthropic、Google Gemini、xAI、Perplexity 等。
- 当前 Provider Catalog、OpenAPI、public docs、translation/adapter 实现。

## 输出

- API/changelog 复核报告。
- 必要的低风险代码或文档更新。
- 后续 backlog 任务。

## 验收标准

- 每个厂商记录官方来源、当前项目状态、差距判断和处理结果。
- 不把非官方文档作为事实源。
- 需要延期的差距有明确 task spec。

## 当前状态

Done。

## 完成结果

- 形成官方来源复核报告：[REP-20260514 主流厂商 API/changelog 复核](../../docs/reports/REP-20260514-mainstream-api-changelog-refresh.md)。
- Perplexity Sonar adapter 补齐低风险 passthrough 字段：web search options、日期过滤、图片过滤、stream mode、reasoning effort、language preference 等。
- Anthropic Messages 入口补官方 snake_case 字段、thinking capture、Native `tool_choice` 与 `thinking.budget_tokens` 下发。
- xAI provider catalog 增加 `grok-4.3`，并记录 2026-05-15 12:00 PT 起退役 slug 重定向风险。
- OpenAI/xAI Responses、Anthropic MCP/service tier、Gemini thinking/toolConfig/grounding 属于较大协议扩展，已拆为 backlog。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.PerplexityWebSearchAdapterTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"`
- `bun run typecheck`
