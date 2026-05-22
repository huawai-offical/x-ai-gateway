# REQ-20260521-009 智能运维总览导航整合与重复面板收口

状态：Done  
日期：2026-05-21  
上游来源：用户指令“补充需求总览的三个导航，全部整合到智能运维总览面板即可，部分功能重合了，删除掉重合部分”

## 背景

当前控制台 `总览` 分组下仍同时暴露 `角色工作台`、`运维总览`、`事件指挥台` 三个入口。三者都承接了“快速总览、风险聚焦、导流排障”的职责，导致：

1. 侧栏总览入口重复，占用首屏导航空间。
2. `/dashboard`、`/ops`、`/incidents` 三页之间存在相似的摘要卡片、风险提醒和导流操作。
3. 用户需要在多个总览页之间跳转，才能完成一次完整的运维判断。

用户要求将这三个总览能力整合到 `智能运维总览` 面板中，删除重合部分，只保留必要且独特的信息。

## 目标

- 将控制台 `总览` 分组的重复入口收敛到 `智能运维总览` 主面板。
- 把 `角色工作台` 与 `事件指挥台` 中仍有价值的摘要信息并入 `/ops` 页面。
- 删除 `/dashboard`、`/incidents` 与 `/ops` 之间重复的总览型面板、重复导流按钮和重复叙事。
- 保持运维、排障、事件与告警的核心路径仍可访问，但以单一总览为第一入口。

## 范围

- `web/src/app/navigation.ts`
- `web/src/app/router.tsx`
- `web/src/features/dashboard/`
- `web/src/features/ops/`
- `web/src/features/incidents/`
- 与上述入口和页面相关的前端测试断言

## 非目标

- 本轮不删除底层事件、告警、链路查询等独立功能页。
- 本轮不改动后端运维 API、事件 API、告警 API 或数据聚合契约。
- 本轮不重做整个控制台信息架构，只处理 `总览` 分组的重复入口与重复面板。

## 风险

- 如果把独特能力也一并删除，可能让事件排障入口变深，影响运维效率。
- 如果只删导航、不补足 `/ops` 面板内容，用户会失去原本在 `dashboard`/`incidents` 首屏可见的信息。
- 如果重定向和面包屑没有同步，容易留下旧书签、旧测试或命令搜索跳转失效。

## 验收标准

1. `总览` 分组默认只保留单一的 `智能运维总览` 主入口，不再并列展示重复总览导航。
2. `/ops` 能承接原 `角色工作台` 与 `事件指挥台` 中仍有价值的摘要信号和高频操作。
3. `/dashboard`、`/incidents` 中与 `/ops` 明显重复的总览型面板、说明和导流已删除或收口。
4. 独立事件、告警、链路、请求日志等功能路径仍可从合适位置进入。
5. 前端 `bun run typecheck` 通过，并补充相关定向 vitest 验证。

## 测试边界

- 检索：导航项、页面标题、跳转与重复面板残留
- 前端：`bun run typecheck`
- 前端：`dashboard`、`ops`、`incidents`、`navigation` 相关定向 vitest

## 关联文档

- [REQ-20260521-007](./REQ-20260521-007-ui-chinese-only-localization.md)
- [REQ-20260521-001](./REQ-20260521-001-console-navigation-credential-proxy-model-ux.md)

## 实现结果

- 已在 `web/src/app/navigation.ts` 将 `总览` 分组收敛为单一 `智能运维总览` 主入口，并同步调整 `/dashboard`、`/incidents` 的 `navTo` 归属。
- 已在 `web/src/app/router.tsx` 将控制台根路由默认落点改为 `/console/ops`，并将旧的成本路由入口重定向到智能运维总览。
- 已将 `/dashboard` 收口为 `角色协同视图`，保留角色分工与批量可信状态；已将 `/incidents` 收口为 `事件处置视图`，保留事件、受影响对象、外发投递与时间线。
- 已删除 `dashboard` / `incidents` 顶部“已收口到主面板”的解释性正文，仅保留必要跳转入口，避免页面重复叙事。
- 已在 `/ops` 主面板中承接总览协同入口、角色视图与事件视图的高频导流，保持独立事件、链路、请求日志等功能可达。

## 验证结果

- 通过：`bun run typecheck`
- 通过：`bun run vitest run src/app/navigation.test.ts src/app/operations-router.test.tsx src/app/layout.test.tsx src/features/dashboard/dashboard-page.test.tsx src/features/ops/ops-page.test.tsx src/features/incidents/incidents-page.test.tsx src/features/request-logs/request-logs-page.test.tsx src/features/accounts/codex-onboarding-page.test.tsx src/features/portal/portal-home-page.test.tsx src/features/traces/traces-page.test.tsx src/features/upstream-cache/upstream-cache-page.test.tsx`

## 遗留事项

- `dashboard` 与 `incidents` 虽已完成总览职责瘦身，但它们仍保留独立功能视图定位；后续如继续做全局汉化残留清理，应与 [REQ-20260521-007](./REQ-20260521-007-ui-chinese-only-localization.md) 一起推进。
