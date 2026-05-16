# REQ-20260514-001 前端路由 HydrateFallback 体验硬化

## 背景

在前一轮 Public Site、Docs、Pricing、Status 客户入口联调后，浏览器与测试日志仍可能出现 React Router lazy route 缺少 `HydrateFallback` 的告警。该告警不会直接阻断页面渲染，但会降低前端 smoke 证据可信度，也会让后续真实浏览器联调难以区分“已知噪音”和真实错误。

## 目标

- 为全局 lazy route 提供统一、轻量、可复用的 hydration fallback。
- 覆盖 public visitor 首屏路由回归，确保公共入口不会触发后台认证 bootstrap，也不会输出 HydrateFallback 告警。
- 将本轮实现、验证命令和剩余风险回写到本需求与任务文件。

## 非目标

- 不重构 React Router 路由拓扑。
- 不改动页面级业务数据加载、后台权限模型或 portal/console 信息架构。
- 不引入新的 loading 组件体系，仅复用现有骨架屏组件。

## 详细设计

1. 在 `web/src/app/router.tsx` 增加统一 `RouteHydrateFallback` 组件，复用 `PageSkeleton`，保证 lazy route hydration 阶段有明确占位。
2. 调整 `lazyPage` 返回值，让每个 lazy route 同时暴露 `Component` 与 `HydrateFallback`。
3. 在 `web/src/app/public-router.test.tsx` 中增加控制台告警回归断言，锁定 public 首屏不会再输出 Hydration fallback 相关告警。

## 范围

- 影响文件：
  - `web/src/app/router.tsx`
  - `web/src/app/public-router.test.tsx`
  - `docs/index.md`
  - `tasks/index.md`
  - `tasks/in-progress/TASK-20260514-001-router-hydrate-fallback-cleanup.md`
- 验证范围：
  - `bun run test -- public-router`
  - 如类型约束变更，再追加 `bun run typecheck`

## 风险

- fallback 组件如果引入过重依赖，可能增加首屏 lazy route 之前的基础 bundle 体积。
- fallback 外层布局如果过度模拟 console/portal，可能和 public 页面产生视觉错位；因此本轮只采用中性骨架屏容器。

## 验收标准

- lazy route 均具备 `HydrateFallback`。
- public visitor home 测试通过，且不触发后台认证 fetch。
- 测试中对 HydrateFallback 告警有明确负向断言。
- 任务移动到 `tasks/done/`，索引状态更新为 Done。

## 状态

- 当前状态：Done
- 创建日期：2026-05-14
- 完成日期：2026-05-14
- 关联任务：[TASK-20260514-001 前端路由 HydrateFallback 告警清理](../../tasks/done/TASK-20260514-001-router-hydrate-fallback-cleanup.md)

## 实现结果

- `web/src/app/router.tsx` 新增统一 `RouteHydrateFallback`，复用现有 `PageSkeleton`。
- `appRoutes` 在创建 router 前递归补齐 lazy route 的静态 `HydrateFallback`，避免 React Router 初始 hydration 阶段输出缺失告警。
- `lazyPage` 仅返回页面 `Component`，避免和静态 fallback 重复注册。
- `web/src/app/public-router.test.tsx` 增加 `console.warn` / `console.error` 负向断言，确保 public 首屏不再出现 HydrateFallback 相关日志噪音。

## 验证结果

- 通过：`bun run test -- public-router`
- 通过：`bun run typecheck`
- 测试中间发现并修正过两类问题：
  - 仅在 lazy loader 返回 `HydrateFallback` 时仍会触发缺失告警。
  - 同时在静态 route 与 lazy loader 返回 fallback 时会触发重复注册告警。

## 遗留问题

- 本轮不改变页面视觉布局，仅处理 route hydration fallback 与日志噪音。
- 后续如要进一步提升首屏观感，可单独拆分 public/portal/console 差异化 skeleton 任务。
