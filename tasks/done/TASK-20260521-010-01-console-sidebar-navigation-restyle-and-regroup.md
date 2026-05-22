# TASK-20260521-010-01 Console 侧栏可读性与导航重分组

状态：Done  
优先级：High  
上游来源：[TASK-20260521-010](./TASK-20260521-010-console-navigation-settings-codex-auth-import-refresh.md)

## 任务类型

子任务

## 背景

侧栏在深色主题下存在高亮背景和文字颜色接近的问题；同时一二级导航中仍有一级栏目只承载一个二级菜单，层级成本高于导航收益。

## 目标

- 调整侧栏 active、hover、图标容器和分组标题颜色。
- 按现役工作流重组导航，避免单子项一级分组。
- 保持旧路由可访问，必要时只调整导航归属。

## 非目标

- 不删除本轮未明确下线的功能页面。
- 不重做整体布局组件。

## 输入

- `web/src/app/navigation.ts`
- `web/src/components/app/app-shell.tsx`
- `web/src/app/navigation.test.ts`
- `web/src/app/layout.test.tsx`

## 输出

- 更新后的侧栏样式和导航分组。
- 更新后的导航/布局测试。

## 影响范围

- Console 侧栏视觉对比度。
- 命令面板、导航分组与面包屑元信息。

## 依赖

- [REQ-20260521-008](../../docs/requirements/REQ-20260521-008-system-theme-dark-default-light-dual-mode.md)
- [REQ-20260521-009](../../docs/requirements/REQ-20260521-009-ops-overview-navigation-consolidation.md)

## 风险

- 导航重分组可能影响用户的肌肉记忆，需要优先保留高频路径可见性。

## 验收标准

- [x] dark/light 下 active 导航项文字清晰。
- [x] 一级分组不再只包含单个二级菜单。
- [x] 导航测试更新并通过。

## 测试边界

- 前端：`bun run vitest run src/app/navigation.test.ts src/app/layout.test.tsx`
- 前端：`bun run typecheck`

## 实现结果

- 侧栏 active 项切换为 `sidebar-primary` 背景和 `sidebar-primary-foreground` 文字，图标容器跟随 active 状态。
- 一级导航归并为智能运维、接入与模型、路由治理、观测记录、用户与计费、系统工具、变更维护、集成扩展。
- `error-rules` 恢复为独立导航路径，面包屑和 route metadata 与新分组对齐。

## 验证结果

- `npm test -- --run src/app/navigation.test.ts`
- `npm run typecheck`
- 浏览器验证 `/console/credentials`：默认 dark 下 active 项背景与文字对比清晰。

## 当前状态

已完成
