# TASK-20260513-006 Public Site、Docs、Pricing、Status 客户入口

状态：Done  
优先级：Medium  
类型：子任务  
父任务：[TASK-20260513-001](../done/TASK-20260513-001-reference-translation-admin-portal-audit.md)  
上游来源：[REQ-20260513-003](../../docs/requirements/REQ-20260513-003-provider-public-site-closure.md)、[REQ-20260513-001](../../docs/requirements/REQ-20260513-001-reference-translation-admin-portal-audit.md)、[REP-20260513](../../docs/reports/REP-20260513-reference-translation-admin-portal-audit.md)

## 背景

当前 `/` 默认重定向到 `/console`，更像内部后台工具。面向客户使用时，缺少公开站点、文档、价格和服务状态入口，客户只能登录 Portal 后查看部分信息。

## 目标

- 增加公开首页，明确产品定位、接入方式和主要能力。
- 增加公开 Docs 入口，覆盖 OpenAI-compatible、Anthropic、Gemini、Codex 接入示例。
- 增加 Pricing 页面，展示套餐、价格、额度和限制。
- 增加 Status 页面，展示公开服务状态、维护公告和历史事件摘要。
- 与 Portal 登录、注册、Console 管理入口清晰区分。

## 非目标

- 不在本任务内实现完整 CMS。
- 不在本任务内实现真实在线支付。
- 不替代 Admin Console 和 Portal 的认证页面。

## 输入

- `web/src/app/router.tsx`
- `web/src/features/portal/`
- `web/src/features/settings/`
- 现有 plans、announcements、channel status API。

## 输出

- Public Site 路由和页面。
- Docs/Pricing/Status 页面。
- 与 Portal/Register/Login 的清晰跳转。

## 影响范围

- 根路由 `/`。
- Public routes。
- Portal 入口。
- SEO/访客体验。

## 依赖

- 套餐、公告、服务状态数据。
- 公开页面的权限边界。

## 风险

- 公开展示不能泄漏后台 provider、内部事件或敏感配置。
- Pricing 与真实计费策略需要一致。

## 验收标准

- 未登录用户访问 `/` 看到公开首页，而不是直接进入 Console。
- Public Docs、Pricing、Status 可访问。
- 登录用户仍可进入 Portal 或 Console。
- 页面内容不暴露敏感后台信息。

## 测试边界

- Frontend route smoke。
- 未登录/已登录跳转检查。
- 浏览器检查桌面和移动首屏。

## 当前状态

Done。

## 实现结果

- 根路径 `/` 改为公开首页，不再默认跳转 `/console`。
- 新增 Public Site 页面：
  - `/`：访客首页，区分 Public Site、Portal、Console。
  - `/docs`：公开文档入口，优先读取 `/public/docs/compatibility`，后端不可用时展示安全 fallback。
  - `/pricing`：公开套餐与额度说明，并声明真实套餐以后台事实源为准。
  - `/status`：公开服务状态，不泄漏 provider secret、账号池和内部路由事件。
- Vite 增加 `/public` API proxy，并对 HTML 请求 bypass，保证前端路由可直达。
- `AdminAuthProvider` 改为公开页面不自动拉取控制台会话；进入受保护 Console 路由时再触发会话恢复。
- Route surface 测试更新为 Public / Portal / Console 三类产品面边界。

## 验证记录

- `bunx vitest run src\app\public-router.test.tsx src\app\operations-router.test.tsx src\features\public\public-pages.test.tsx src\app\route-surfaces.test.ts`：通过。
- `bun run typecheck`：通过。
- `bun run lint`：通过。
- Browser smoke：`http://127.0.0.1:5174/`、`/docs`、`/pricing`、`/status` 桌面与 390x844 移动视口均能渲染目标文本。

## 遗留与后续

- `/docs` 在未联调真实后端时会展示 fallback；后端 `/public/docs/compatibility` 已由既有 `PublicDocsBundleService` 测试覆盖。
- 浏览器日志仍可见 React Router lazy route 的既有 `HydrateFallback` warning，不影响页面渲染，可后续统一处理。
