# TASK-20260521-009 智能运维总览导航整合与重复面板收口

状态：已完成  
优先级：High  
上游来源：[REQ-20260521-009](../../docs/requirements/REQ-20260521-009-ops-overview-navigation-consolidation.md)

## 任务类型

父任务

## 背景

控制台 `总览` 分组下同时存在 `角色工作台`、`运维总览`、`事件指挥台` 三个入口，且 `/dashboard`、`/ops`、`/incidents` 存在明显的总览型重复内容。用户要求将三块内容整合到 `智能运维总览` 面板中，并删除重合部分。

## 目标

- 收敛 `总览` 分组入口，只保留单一主总览。
- 将仍有价值的角色视角和事件摘要并入 `/ops`。
- 删除重复总览面板、重复导流和重复叙事。

## 非目标

- 不删除链路、告警、请求日志、系统事件等独立功能页。
- 不改动后端 API 与聚合契约。

## 输入

- `web/src/app/navigation.ts`
- `web/src/app/router.tsx`
- `web/src/features/dashboard/`
- `web/src/features/ops/`
- `web/src/features/incidents/`

## 输出

- 收敛后的总览导航与页面结构
- 更新后的测试断言
- 回写后的需求与任务状态

## 影响范围

- Console `总览` 分组信息架构
- `/dashboard`、`/ops`、`/incidents` 三页的首屏结构与导流
- 相关前端测试

## 依赖

- [TASK-20260521-007](../in-progress/TASK-20260521-007-ui-chinese-only-localization.md)

## 风险

- 删除入口过猛会影响已有使用路径。
- 合并时若没有同步 breadcrumbs、搜索项和 redirect，容易留下断链。

## 验收标准

- [x] `总览` 分组只保留单一的 `智能运维总览` 主入口。
- [x] `/ops` 承接角色摘要与事件聚焦所需的总览信息。
- [x] `/dashboard`、`/incidents` 中重复总览面板完成收口。
- [x] 相关跳转、redirect 与测试同步通过。
- [x] `bun run typecheck` 通过。
- [x] 定向 vitest 通过。

## 测试边界

- 检索导航与总览重复残留
- 前端：`bun run typecheck`
- 前端：`navigation`、`dashboard`、`ops`、`incidents` 定向 vitest

## 关联任务

- [TASK-20260521-009-01](./TASK-20260521-009-01-ops-overview-surface-merge.md)
- [TASK-20260521-007](../in-progress/TASK-20260521-007-ui-chinese-only-localization.md)

## 当前状态

已完成

## 实现结果

- 已完成 `总览` 分组导航收敛，只保留 `智能运维总览` 作为主入口，并将旧的控制台默认落点重定向到 `/console/ops`。
- 已通过子任务 [TASK-20260521-009-01](./TASK-20260521-009-01-ops-overview-surface-merge.md) 完成 `/dashboard`、`/ops`、`/incidents` 的职责重整与重复面板下线。
- 已同步更新 `navigation`、`router`、`dashboard`、`ops`、`incidents` 与 `layout` 相关测试，确保 redirect、跳转和总览入口一致。

## 验证结果

- 通过：`bun run typecheck`
- 通过：`bun run vitest run src/app/navigation.test.ts src/app/operations-router.test.tsx src/app/layout.test.tsx src/features/dashboard/dashboard-page.test.tsx src/features/ops/ops-page.test.tsx src/features/incidents/incidents-page.test.tsx src/features/request-logs/request-logs-page.test.tsx src/features/accounts/codex-onboarding-page.test.tsx src/features/portal/portal-home-page.test.tsx src/features/traces/traces-page.test.tsx src/features/upstream-cache/upstream-cache-page.test.tsx`
