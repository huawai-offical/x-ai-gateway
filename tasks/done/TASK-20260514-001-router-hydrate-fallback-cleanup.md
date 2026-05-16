# TASK-20260514-001 前端路由 HydrateFallback 告警清理

## Task Spec

### 背景

Public Site 与 Console 路由已经拆分，但 lazy route 在 hydration 阶段缺少统一 fallback，测试或浏览器日志会出现 React Router 告警，影响前端联调证据质量。

### 目标

- 为 `lazyPage` 生成的路由统一注入 `HydrateFallback`。
- 为 public 首屏路由增加“无 HydrateFallback 告警”的回归测试。
- 保持 public route 不触发 admin auth bootstrap 的既有验收。

### 非目标

- 不调整业务页面加载数据。
- 不重排一级/二级菜单。
- 不修改后台认证或客户门户权限逻辑。

### 上游来源

- 用户请求：`/goal 继续推进项目进度`
- 需求文档：[REQ-20260514-001 前端路由 HydrateFallback 体验硬化](../../docs/requirements/REQ-20260514-001-router-hydration-fallback-hardening.md)

### 输入

- `web/src/app/router.tsx`
- `web/src/app/public-router.test.tsx`
- 现有 `PageSkeleton` 组件

### 输出

- 路由 lazy loader 返回 `HydrateFallback`。
- public router 测试覆盖日志噪音回归。
- 本任务状态和验证结果回写。

### 影响范围

- 前端 lazy route hydration 占位体验。
- Vitest public router smoke。

### 依赖

- React Router v7 lazy route 支持 `HydrateFallback` 导出。
- 现有 `PageSkeleton` 组件可在 app 层复用。

### 风险

- 类型推断可能需要补充 route lazy 返回类型。
- fallback 容器样式必须保持中性，避免 public/portal/console 首屏出现明显错位。

### 验收标准

- `bun run test -- public-router` 通过。
- 测试断言 `console.warn` / `console.error` 中不包含 `HydrateFallback`。
- `tasks/index.md` 与 `docs/index.md` 已建立关联。

### 测试边界

- 自动化测试：public router 单测。
- 不做全量浏览器截图；本任务关注日志噪音与 hydration fallback，不改变页面布局。

### 关联文档

- [REQ-20260514-001 前端路由 HydrateFallback 体验硬化](../../docs/requirements/REQ-20260514-001-router-hydration-fallback-hardening.md)

### 关联任务

- 上游父任务：本任务作为本轮推进的父子合一小任务，边界足够独立。
- 后续任务：暂无；如测试暴露更大范围路由问题，再拆分新任务。

### 当前状态

- 状态：Done
- 创建日期：2026-05-14
- 完成日期：2026-05-14

## 执行记录

- 2026-05-14：创建任务，准备实现统一 route HydrateFallback 与测试断言。
- 2026-05-14：在 `web/src/app/router.tsx` 增加 `RouteHydrateFallback` 与 lazy route 递归补齐逻辑。
- 2026-05-14：在 `web/src/app/public-router.test.tsx` 增加 public 首屏无 HydrateFallback 告警断言。
- 2026-05-14：第一次测试发现 lazy loader 返回 fallback 仍无法消除初始 hydration 告警，调整为路由对象静态补齐。
- 2026-05-14：第二次测试发现静态 route 与 lazy loader 双重 fallback 会触发重复注册告警，移除 lazy loader 返回值中的 fallback。

## 验证记录

- 通过：`bun run test -- public-router`
- 通过：`bun run typecheck`

## 验收结果

- 已满足：lazy route 均在创建 router 前具备静态 `HydrateFallback`。
- 已满足：public visitor home 不触发 admin auth `fetch`。
- 已满足：public router 测试断言日志中不包含 `HydrateFallback`。
- 已满足：本任务可移动到 `tasks/done/`。
