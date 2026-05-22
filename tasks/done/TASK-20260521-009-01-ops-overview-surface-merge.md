# TASK-20260521-009-01 智能运维总览页面合并与导航收口

状态：已完成  
上游来源：[TASK-20260521-009](./TASK-20260521-009-ops-overview-navigation-consolidation.md)

## 背景

`/dashboard`、`/ops`、`/incidents` 三页都承担了总览型职责，但用户要求统一回收到 `智能运维总览`，并删除重复部分。

## 目标

- 调整 `navigation.ts` 与必要路由，使 `总览` 分组只保留单一主入口。
- 将 `dashboard` 与 `incidents` 中仍有价值的总览型信息并入 `ops`。
- 删除 `dashboard` 与 `incidents` 中重复的总览面板、重复 CTA 与重复说明。

## 非目标

- 不删除事件详情、链路排障、请求日志等独立任务页。
- 不重构非总览型页面的内部数据表格。

## 输入

- `web/src/app/navigation.ts`
- `web/src/features/dashboard/dashboard-page.tsx`
- `web/src/features/ops/ops-page.tsx`
- `web/src/features/incidents/incidents-page.tsx`
- 相关测试文件

## 输出

- 收口后的总览导航
- 合并后的智能运维总览页面
- 同步更新的前端测试

## 影响范围

- 控制台侧栏 `总览` 分组
- `/dashboard`
- `/ops`
- `/incidents`

## 依赖

- `dashboard`、`ops`、`incidents` 现有组件结构

## 验收标准

- [x] 导航只保留单一智能运维总览入口。
- [x] `/ops` 吸收原总览页中仍有价值的信息。
- [x] 重复面板与重复按钮被移除或合并。
- [x] 页面测试同步更新并通过。

## 测试边界

- 前端：`bun run test src/app/navigation.test.ts src/features/dashboard/dashboard-page.test.tsx src/features/ops/ops-page.test.tsx src/features/incidents/incidents-page.test.tsx`
- 前端：`bun run typecheck`

## 当前状态

已完成

## 实现结果

- 已在 `navigation.ts` 中将 `总览` 分组收敛为单一 `智能运维总览` 入口。
- 已将 `/dashboard` 收口为 `角色协同视图`，保留角色分工与批量可信状态；已将 `/incidents` 收口为 `事件处置视图`，保留事件、受影响对象、外发投递与时间线。
- 已在 `/ops` 中补充总览协同入口，承接角色协同视图、事件处置视图与链路追踪等高频操作。
- 已删除 `dashboard` 与 `incidents` 的重复说明卡和重复总览叙事，仅保留必要功能入口。

## 验证结果

- 通过：`bun run typecheck`
- 通过：`bun run vitest run src/app/navigation.test.ts src/features/dashboard/dashboard-page.test.tsx src/features/ops/ops-page.test.tsx src/features/incidents/incidents-page.test.tsx`
