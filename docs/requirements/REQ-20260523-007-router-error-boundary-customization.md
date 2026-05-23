# REQ-20260523-007 前端路由错误页自定义

状态：Done  
日期：2026-05-23  
关联任务：[TASK-20260523-008 前端路由错误页自定义](../../tasks/done/TASK-20260523-008-router-error-boundary-customization.md)

## 背景

前端路由使用 React Router lazy route 加载页面模块。当动态导入失败、路由渲染异常或其他 route 层错误发生时，当前会展示 React Router 默认开发错误页，例如：

```text
Unexpected Application Error!
Failed to fetch dynamically imported module: http://localhost:5173/src/features/keys/keys-page.tsx
TypeError: Failed to fetch dynamically imported module: http://localhost:5173/src/features/keys/keys-page.tsx
Hey developer
```

该页面面向开发者，包含英文提示和框架内部说明，不符合控制台、Portal 与公开页的中文单语体验要求，也会让普通用户误以为系统完全不可恢复。

## 目标

- 为全站 React Router 路由提供统一 `errorElement`，替换默认 `Unexpected Application Error` 页面。
- 对动态导入失败给出明确、可恢复的中文提示，并提供刷新当前页面、返回首页、返回控制台等操作。
- 在测试中覆盖 route error boundary 渲染，防止后续移除或遗漏。
- 完成本地文档与任务回写，记录实现结果、验证命令和剩余风险。

## 范围

- 前端路由层错误体验：
  - `web/src/app/router.tsx`
  - 新增或复用路由错误页组件
  - 对应 Vitest 回归测试
- 本地追溯文件：
  - `docs/requirements/REQ-20260523-007-router-error-boundary-customization.md`
  - `tasks/done/TASK-20260523-008-router-error-boundary-customization.md`
  - `docs/index.md`
  - `tasks/index.md`

## 非目标

- 不处理 API 请求级业务错误 toast 或表单内联错误。
- 不改变现有 route 拓扑、权限模型、登录跳转或菜单结构。
- 不引入新的错误上报平台；本轮只提供用户可读错误页与测试护栏。
- 不解决 Vite dev server 或网络本身导致的 chunk 请求失败根因。

## 方案

1. 新增统一 `RouteErrorBoundary` 组件，使用 React Router `useRouteError` 读取错误对象并归一化展示。
2. 对动态导入失败、404 route response、一般 `Error` 和未知异常分别生成中文标题、说明与技术细节。
3. 在 `appRoutes` 创建前递归补齐 `errorElement`，覆盖 top-level、console child、portal/public lazy route。
4. 增加单测：构造抛错 route，验证自定义错误页出现，并验证默认 `Unexpected Application Error` 不出现。

## 风险

- 如果错误发生在 React Router 初始化前，route `errorElement` 无法捕获；该场景仍需要应用级 ErrorBoundary，后续可单独拆分。
- 如果动态 chunk 长时间不可达，刷新按钮只能触发重新加载，不能保证立即恢复。
- 错误详情需要足够帮助排障，但不能让界面再次变成框架默认开发错误页。

## 验收标准

- 路由错误时展示中文自定义错误页，而不是 `Unexpected Application Error`。
- 动态导入失败的错误信息会被识别为“页面资源加载失败”。
- 页面提供刷新、返回控制台、返回首页的恢复操作。
- `bun run test -- router-error-boundary` 通过。
- 如类型或 route 结构变化，追加 `bun run typecheck`。

## 实现结果

- 新增 `web/src/app/route-error-boundary.tsx`，提供统一中文路由错误页，包含错误标题、说明、可选 `HTTP` 状态、技术细节、刷新当前页面、返回控制台、返回首页。
- 新增 `web/src/app/route-error-normalizer.ts`，集中归一化动态 import 失败、React Router response error、普通 `Error` 与未知异常。
- `web/src/app/router.tsx` 将原 `withLazyRouteHydrateFallback` 扩展为 `withRouteDefaults`，保留 lazy route `HydrateFallback`，并递归补齐 `errorElement`。
- `web/src/app/router.tsx` 为 Console 子路由和全局路由增加 `*` 兜底，未匹配地址也显示同一套中文 404 页面。
- 新增 `web/src/app/router-error-boundary.test.tsx`，覆盖动态模块加载失败文案、默认错误页不出现、lazy console route 注入 `errorElement`、未匹配路由自定义 404、错误归一化。

## 测试/验证

- 通过：`bun run test -- router-error-boundary`
- 通过：`bun run typecheck`
- 通过：`bun run build`
- 部分通过：`bun run lint`
  - 本次新增代码 lint 已修复。
  - 全量 lint 仍有既有无关问题：`web/src/components/app/theme-switch.tsx:12`、`web/src/features/keys/keys-page.tsx:276`、`web/src/features/keys/keys-page.tsx:288`、`web/src/features/network/tls-profiles-page.tsx:424`。
- 浏览器验证：
  - 使用临时 Vite 实例 `http://127.0.0.1:5174/`。
  - 首页正常渲染，未出现 `Unexpected Application Error`，console error/warn 为 0。
  - 访问 `/missing-route-for-error-boundary-browser-check` 显示中文 `页面不存在` 与 `HTTP 404`，提供刷新、返回控制台、返回首页。
  - 点击 `返回首页` 后 URL 回到 `/`，首页 `x-ai-gateway` 标题可见，console error/warn 为 0。
  - Browser 截图能力连续触发 `Page.captureScreenshot` 超时，本轮以 DOM、URL、交互和 console health 作为浏览器证据。

## 遗留问题

- `errorElement` 覆盖 React Router route 层错误；如果异常发生在 `RouterProvider` 外层 provider 初始化阶段，仍需后续单独增加应用级 ErrorBoundary。
- 动态 chunk 文件缺失或 dev server 卡死时，错误页可提供恢复动作，但根因仍依赖重新构建、刷新资源或恢复服务。
- 全量 lint 的既有无关问题未在本任务中处理，避免扩大修改范围。
