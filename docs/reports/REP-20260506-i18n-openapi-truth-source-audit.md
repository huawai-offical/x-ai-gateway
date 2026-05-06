# REP-20260506 i18n 与 OpenAPI 事实源审计

关联需求：[REQ-20260506-011 文档、i18n 与 OpenAPI 事实源修复](../requirements/REQ-20260506-011-docs-i18n-openapi-truth-source.md)  
关联任务：[TASK-20260506-013 文档、i18n 与 OpenAPI 事实源修复](../../tasks/done/TASK-20260506-013-docs-i18n-openapi-truth-source.md)

## 结论

- 默认事实源应为仓库内 `docs/index.md`、`tasks/index.md` 和公开 docs bundle。
- `README.md` 的 Notion 默认入口属于历史遗留，需要移除。
- 后端公开文档已支持 `zh-CN` 与 `en-US`，前端控制台目前以 `zh-CN` 硬编码文案为主，尚未建立运行时语言切换。
- 当前 OpenAPI 最低可维护范围应覆盖公开接入入口、docs bundle、OpenAI-compatible、Claude/Gemini 兼容路径、Media provider matrix 和 OAuth 入口说明。

## i18n 最低策略

- `zh-CN`：管理端和 Portal 的默认 UI 语言，当前保持完整。
- `en-US`：公开 docs bundle、OpenAPI 描述和 SDK 示例必须提供英文基础文本。
- 前端运行时切换：暂不作为本轮交付，后续需要先抽取 navigation、页面标题、表格列名、按钮和错误提示。

## OpenAPI 最低策略

- 公开维护 `/public/docs/openapi.json`。
- 不把内部 Admin 全量接口一次性纳入公开 OpenAPI。
- schema 以接入方最常用的 Chat、Responses、Messages、Gemini generateContent、Media provider matrix 和 docs endpoint 为核心。
- SDK 示例只声明 tested shape，不承诺无真实 provider 凭证时可完成真实上游调用。

## 缺口清单

| 类别 | 当前状态 | 后续建议 |
| --- | --- | --- |
| README | 已从 Notion 默认入口切回本地事实源 | 每次新增 docs/tasks 后同步索引 |
| docs bundle | 已包含 compatibility、provider presets、CLI、examples、errors | 增加 OpenAPI URL、SDK targets、i18n policy |
| OpenAPI | 新增最小公开 spec | 后续可接入生成器或快照发布 |
| 前端 i18n | 以 `zh-CN` 硬编码为主 | 先抽取导航和公共组件文案 |
| SDK 示例 | curl/OpenAI SDK/Claude Code/Gemini CLI/Codex CLI | 后续补 Python、Go、Java |
