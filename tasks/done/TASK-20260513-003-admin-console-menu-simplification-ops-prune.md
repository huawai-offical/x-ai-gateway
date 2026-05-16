# TASK-20260513-003 Admin Console 菜单精简与无效运维能力下线

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260513-001](../done/TASK-20260513-001-reference-translation-admin-portal-audit.md)  
上游来源：[REQ-20260513-002](../../docs/requirements/REQ-20260513-002-high-priority-gap-closure-implementation.md)、[REQ-20260513-001](../../docs/requirements/REQ-20260513-001-reference-translation-admin-portal-audit.md)、[REP-20260513](../../docs/reports/REP-20260513-reference-translation-admin-portal-audit.md)

## 背景

当前 Admin Console 功能覆盖面很广，但一级菜单和二级菜单偏重，右上角 Workspace/Environment/Tenant Slot 等空间区分能力目前主要是前端本地状态，没有明确业务作用。用户明确认为这类运维能力不会使用，希望删除或收敛。

## 目标

- 删除或隐藏右上角 Workspace/Environment/Tenant Slot 上下文切换。
- 移除顶部无实际后端语义的 environment/tenant slot badge。
- 重排 Admin Console 一级菜单和二级菜单，使主路径更清晰。
- 将安装初始化、变更编排、维护窗口、恢复检查点、维护 Run、备份恢复、版本升级、回滚任务等低频运维能力折叠为高级运维，或从默认菜单移除。
- 去重 `ops/probes` 和 `network/probes` 等语义相近入口。
- 为高级调试入口增加明确分组，避免干扰普通管理员。

## 非目标

- 不删除后端 API 和历史数据。
- 不在本任务内重做整体视觉设计。
- 不改变权限模型本身，除非入口隐藏需要配套 route guard。

## 输入

- `web/src/app/navigation.ts`
- `web/src/app/router.tsx`
- `web/src/components/app/app-shell.tsx`
- `web/src/components/app/workspace-context.tsx`
- `web/src/features/operations/`
- `web/src/features/network/`
- `web/src/features/workbench/`

## 输出

- 精简后的 Admin Console 菜单。
- 删除或隐藏后的上下文切换组件。
- 路由重定向或隐藏策略。
- 更新后的前端测试与文档记录。

## 影响范围

- Admin Shell。
- Navigation。
- Console routes。
- 低频运维页面入口。

## 依赖

- 当前 React Router 配置。
- 当前权限与认证页面。

## 风险

- 隐藏入口后可能影响已有测试路径。
- 删除上下文切换组件需要确认没有其他页面依赖 context 值。
- 低频运维入口若完全删除，需要保留深链或高级模式策略。

## 验收标准

- 默认 Console 顶部不再显示无业务意义的 Workspace/Environment/Tenant Slot 切换。
- 一级菜单控制在更清晰的 6 至 7 组以内。
- 低频运维能力默认不可见或归入高级运维。
- 菜单项名称面向用户，而不是内部实现对象。
- 现有关键页面仍可通过合理路径访问。

## 测试边界

- 前端单测或 route smoke 覆盖主要菜单路径。
- 浏览器手工检查桌面和移动布局不溢出。
- 确认隐藏的低频运维路由不会在默认导航中出现。

## 实施结果

- 从 `AppProviders` 和 `AppShell` 移除默认 Workspace/Environment/Tenant Slot 上下文切换。
- 删除 `context-switcher.tsx` 与 `workspace-context.tsx`，避免保留无后端事实源的 UI 状态。
- Admin 导航从原来的重型分组收敛为“总览、接入、Codex、路由、观测、用户与计费、系统设置”。
- 低频运维页面保留路由深链，但不再出现在默认导航中。
- `ops/probes` 与 `network/probes` 默认导航去重，保留“拨测记录”主入口。
- Ops Realtime Provider 收敛到 Console 布局内，避免 Portal 页面连接后台 WebSocket。
- 修复 Codex onboarding 既有同步 effect lint 问题，保持全量 lint 可通过。

## 验证记录

- `bun run typecheck`
- `bun run lint`
- `bun run test -- src/app/navigation.test.ts src/app/layout.test.tsx src/features/portal/portal-home-page.test.tsx`
- Browser smoke：Console 默认导航无“控制平面/预发/共享”上下文切换文本，无“安装初始化/维护窗口/回滚任务”默认入口。

## 当前状态

Done。
