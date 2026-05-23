# TASK-20260523-008 前端路由错误页自定义

状态：Done  
优先级：High  
来源：User Request  
关联需求：[REQ-20260523-007 前端路由错误页自定义](../../docs/requirements/REQ-20260523-007-router-error-boundary-customization.md)  
关联报告：无

## Task Spec

### 背景

用户反馈任意错误或错误页面会显示 React Router 默认开发错误页，典型文案为 `Unexpected Application Error!` 与 `Failed to fetch dynamically imported module`。该页面不符合当前项目中文单语 UI 与可恢复错误体验要求。

### 目标

- 为 React Router route tree 统一挂载自定义 `errorElement`。
- 自定义错误页覆盖动态 import 失败、route response 错误、普通 Error 与未知错误。
- 增加测试锁定错误页中文渲染与默认错误页不出现。
- 完成需求、任务和索引回写。

### 非目标

- 不修改 API 请求错误 toast 机制。
- 不重构 `AppLayout`、登录态、Portal/Public/Console 路由边界。
- 不接入外部错误监控。
- 不修复 Vite dev server chunk 失效本身。

### 上游来源

- 用户请求：自定义任何错误或错误页面，避免显示 React Router 默认 `Unexpected Application Error`。
- 需求文档：[REQ-20260523-007 前端路由错误页自定义](../../docs/requirements/REQ-20260523-007-router-error-boundary-customization.md)

### 输入

- `web/src/app/router.tsx`
- 现有 UI 基础样式、lucide icon、React Router v7 errorElement 能力
- 当前触发样例：`/src/features/keys/keys-page.tsx` 动态导入失败

### 输出

- 新增统一路由错误页组件。
- 路由配置递归补齐 `errorElement`。
- 新增路由错误边界测试。
- 本任务完成后移动到 `tasks/done/` 并更新索引。

### 影响范围

- 所有前端路由错误展示，包括 Console、Portal、Public 与 Login。
- `appRoutes` 派生逻辑。
- 前端 Vitest 测试。

### 依赖

- React Router `useRouteError`、`isRouteErrorResponse`。
- Vite/React 项目现有 TypeScript 与 Vitest 环境。

### 风险

- 递归注入 route 字段需保持 `HydrateFallback` 逻辑不回退。
- 如果错误发生在 router 外层 providers 初始化阶段，本任务不会捕获。
- 如果 chunk 文件缺失，刷新操作依赖 dev server/build 产物恢复。

### 验收标准

- 自定义错误页显示中文标题、恢复动作与简短技术细节。
- 动态导入失败归类为页面资源加载失败。
- 测试不再出现默认 `Unexpected Application Error`。
- `bun run test -- router-error-boundary` 通过。
- `bun run typecheck` 通过或记录无法执行原因。

### 测试边界

- 自动化测试：route error boundary 组件与路由注入行为。
- 类型检查：确保 route 配置字段类型兼容。
- 浏览器验证：如本地 dev server 可启动，打开错误测试路由或构造错误页面确认视觉可用；若无法稳定复现 chunk 拉取失败，以测试覆盖为准。

### 关联文档

- [REQ-20260523-007 前端路由错误页自定义](../../docs/requirements/REQ-20260523-007-router-error-boundary-customization.md)

### 关联任务

- 父任务：本任务作为父子合一小任务，边界独立且可单独验收。
- 关联历史任务：[TASK-20260514-001 前端路由 HydrateFallback 告警清理](../done/TASK-20260514-001-router-hydrate-fallback-cleanup.md)

### 当前状态

- 状态：Done
- 创建日期：2026-05-23
- 完成日期：2026-05-23

## 实现记录

- 2026-05-23：创建任务，准备实现统一 route `errorElement` 与测试。
- 2026-05-23：新增 `RouteErrorBoundary`、`RouteNotFoundPage` 与错误归一化模块。
- 2026-05-23：将路由默认注入逻辑扩展为 `withRouteDefaults`，递归补齐 `HydrateFallback` 与 `errorElement`。
- 2026-05-23：为 Console 子路由和全局路由增加 `*` 兜底，未匹配路由显示中文 404。
- 2026-05-23：新增 `router-error-boundary.test.tsx` 覆盖动态 import 失败、默认错误页不出现、路由注入和 404 兜底。
- 2026-05-23：修复新增文件 Fast Refresh lint 问题，将非组件逻辑移动到 `route-error-normalizer.ts`。

## 测试/验证

- 通过：`bun run test -- router-error-boundary`
- 通过：`bun run typecheck`
- 通过：`bun run build`
- 部分通过：`bun run lint`
  - 新增代码 lint 已干净。
  - 全量 lint 仍被既有无关问题阻断：
    - `web/src/components/app/theme-switch.tsx:12`
    - `web/src/features/keys/keys-page.tsx:276`
    - `web/src/features/keys/keys-page.tsx:288`
    - `web/src/features/network/tls-profiles-page.tsx:424`
- 浏览器验证：
  - 临时启动 `http://127.0.0.1:5174/`，验证后已停止 5174 服务。
  - 首页正常加载，无框架默认错误页，console error/warn 为 0。
  - 未匹配路由显示中文 `页面不存在`、`HTTP 404`、刷新/返回控制台/返回首页动作。
  - 点击 `返回首页` 可返回 `/` 并显示 `x-ai-gateway` 首页。
  - Browser 截图能力连续超时，未产出截图文件；本轮保留 DOM、URL、交互和 console health 验证。

## 遗留问题

- Provider 初始化阶段的异常不在 route `errorElement` 捕获范围内，后续可拆应用级 ErrorBoundary。
- 动态资源加载失败的恢复动作依赖资源恢复或重新刷新，错误页本身不修复 dev server 或部署产物问题。
- 全量 lint 的既有无关问题需独立任务处理。
