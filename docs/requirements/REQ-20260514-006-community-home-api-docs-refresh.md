# REQ-20260514-006 社区首页体验与主流 API 文档复核

## 背景

用户反馈当前社区用户首页存在两类问题：一是部分样式导致文字可读性不足；二是部分链接会进入 404 或 lazy fallback 状态。同时，主流厂商 API 与 changelog 可能已有更新，需要复核当前网关的参数、请求和对接面是否需要调整。

## 目标

- 优化社区/Portal 首页排版、对比度、信息密度和移动端可读性。
- 修复首页入口中会造成 404、fallback 或误导用户的链接。
- 阅读主流厂商官方 API 文档与 changelog，输出当前项目需要跟进的接口差距。
- 对低风险且边界明确的 API 文档/参数差异直接更新；较大协议改动拆成后续任务。

## 非目标

- 不重做整个 Portal 信息架构。
- 不引入未验证的第三方文档或非官方 SDK 行为作为事实源。
- 不在没有明确边界时大规模重写协议翻译层。

## 子任务

- [TASK-20260514-007 社区首页排版、对比度与链接硬化](../../tasks/done/TASK-20260514-007-community-home-ui-route-hardening.md)
- [TASK-20260514-008 主流厂商 API/changelog 复核与参数差距闭环](../../tasks/done/TASK-20260514-008-mainstream-api-docs-changelog-refresh.md)

## 实现结果

- 社区首页、Portal shell、登录页与公开首页完成对比度和排版硬化，避免浅色文字叠浅底、移动端表格挤压和无效透明色。
- 公开页 footer 的 `OpenAPI` 入口改为 `/docs`，旧 `/public/docs/openapi.json` 前端路径落到公开文档页，不再进入 404。
- `/docs` 后端不可用时展示用户可理解的本地文档状态，不再暴露 `fallback` 开发态文案，OpenAPI JSON 未就绪时展示禁用按钮。
- 完成 OpenAI、Anthropic、Google Gemini、xAI、Perplexity 官方 API/changelog 复核，低风险差距已更新；较大协议扩展已拆分并在 [REQ-20260514-007](REQ-20260514-007-mainstream-api-parity-backlog-closure.md) 闭环。

## 已闭环扩展任务

- [TASK-20260514-009 OpenAI/xAI Responses 字段 parity 与 cache/header 保真](../../tasks/done/TASK-20260514-009-openai-xai-responses-field-parity.md)
- [TASK-20260514-010 Anthropic MCP/service tier/container/context management 字段下发](../../tasks/done/TASK-20260514-010-anthropic-mcp-service-tier-field-parity.md)
- [TASK-20260514-011 Gemini thinkingConfig/toolConfig/URL context/Grounding 参数 parity](../../tasks/done/TASK-20260514-011-gemini-thinking-toolconfig-grounding-parity.md)

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.PerplexityWebSearchAdapterTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.anthropic.AnthropicMessagesControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"`
- `bun run test -- portal-home-page.test.tsx public-pages.test.tsx`
- `bun run typecheck`
- Browser smoke：`/`、`/portal` 后端离线态、`/docs`、`/public/docs/openapi.json` 重定向、390x844 移动视口均无 404、无可见 fallback 文案、无 console error。

## 验收标准

- 社区首页首屏文字清晰，不出现浅色文字叠浅底、按钮文字不可辨或表格文字挤压。
- 社区首页主要链接均指向已注册路由或外部有效地址，不触发 404 fallback。
- 至少覆盖 OpenAI、Anthropic、Google Gemini、xAI、Perplexity 等主流 API 官方文档/changelog。
- 对已确认的低风险差距完成实现或文档更新；主流 API parity 扩展差距已拆分并移动到 `tasks/done/`。

## 状态

Done。
