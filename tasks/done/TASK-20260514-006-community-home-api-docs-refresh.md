# TASK-20260514-006 社区首页体验与主流 API 文档复核

状态：Done  
优先级：High  
类型：父任务  
上游来源：[REQ-20260514-006](../../docs/requirements/REQ-20260514-006-community-home-api-docs-refresh.md)、用户请求

## 背景

社区用户首页和主流厂商 API 对接都属于用户主路径：前者影响社区用户是否能顺利理解和进入功能，后者影响网关协议兼容性与后续维护节奏。

## 目标

- 闭环社区首页 UI/UX 与链接可达性。
- 闭环主流厂商 API/changelog 复核，并把差距转成实现或后续任务。

## 非目标

- 不一次性重构所有 Portal 页面。
- 不在本父任务内实现所有可能的厂商新特性。

## 子任务

- [TASK-20260514-007 社区首页排版、对比度与链接硬化](TASK-20260514-007-community-home-ui-route-hardening.md)
- [TASK-20260514-008 主流厂商 API/changelog 复核与参数差距闭环](TASK-20260514-008-mainstream-api-docs-changelog-refresh.md)

## 测试边界

- 前端单测和路由测试。
- Browser 插件桌面/移动视口 smoke。
- 官方文档/changelog 来源记录。

## 当前状态

Done。

## 完成结果

- 子任务 TASK-20260514-007 已完成社区首页对比度、排版、链接和公开 docs fallback 文案硬化。
- 子任务 TASK-20260514-008 已完成主流厂商 API/changelog 官方复核、低风险代码更新和后续 backlog 拆分。
- API 复核报告已落地：[REP-20260514 主流厂商 API/changelog 复核](../../docs/reports/REP-20260514-mainstream-api-changelog-refresh.md)。

## 验证记录

- Gradle focused tests：通过。
- Web focused tests：通过。
- `bun run typecheck`：通过。
- Browser smoke：桌面首页、Portal 后端离线态、公开文档、旧 OpenAPI 路径重定向、移动首页均通过。

## 后续边界

- OpenAI/xAI Responses 深层字段 parity、Anthropic MCP/service tier 治理、Gemini thinking/toolConfig/grounding parity 不混入本父任务，已拆入 backlog。
