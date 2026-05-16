# REP-20260507 当前 Backlog 优先级队列

状态：Done  
日期：2026-05-07  
关联任务：[TASK-20260507-015 当前 Backlog 优先级队列编排](../../tasks/done/TASK-20260507-015-backlog-priority-roadmap.md)

## 排序原则

- 先做会影响全局正确性的地基：Codex 请求保真、产品面边界、权限隔离。
- 再做真实可用闭环：Codex 官方账号 adapter、Console 命名空间、UI/UX 验收基线、接入向导。
- 然后做运营效率：账号池热切换、实时观测、过滤命中和 usage。
- 最后做全局硬化：跨页面表单、移动端、空态、错误态和体验验收。

## 优先级队列

| 排期 | 任务 | 定位 | 依赖/说明 |
| --- | --- | --- | --- |
| P0-01 | [TASK-20260507-002 Codex CLI 请求保真、Session 粘性与反向代理兼容](../../tasks/backlog/TASK-20260507-002-codex-cli-request-fidelity-sticky-session.md) | Codex 反代协议地基 | 先确保 header、SSE、session affinity 和 Nginx 兼容，否则后续 smoke 与观测不可靠 |
| P0-02 | [TASK-20260507-009 Portal/Admin 路由、身份与产品面边界基线](../../tasks/backlog/TASK-20260507-009-portal-admin-route-identity-boundary.md) | 产品面边界地基 | 为 Portal、Console、Public Site 的路由和术语建立事实源 |
| P0-03 | [TASK-20260507-013 Portal/Admin API 权限隔离、审计与越权回归](../../tasks/backlog/TASK-20260507-013-portal-admin-permission-audit-regression.md) | 安全边界地基 | 在扩展 UI 前先锁定普通用户、管理员和代管操作的权限边界 |
| P0-04 | [TASK-20260507-001 Codex 官方账号真实适配、配额刷新与反代 Smoke](../../tasks/backlog/TASK-20260507-001-codex-official-account-real-adapter-smoke.md) | 真实账号闭环 | 基于 P0-01 的请求契约做真实 adapter、quota refresh 和 smoke |
| P1-05 | [TASK-20260507-014 Portal/Console UI/UX 验收体系与组件硬化](../../tasks/backlog/TASK-20260507-014-portal-console-ux-acceptance-system.md) | UI 验收基线 | 先定验收规则，后续 Portal/Console 页面按同一标准实现 |
| P1-06 | [TASK-20260507-011 Admin Console 命名空间迁移与旧路由兼容](../../tasks/backlog/TASK-20260507-011-admin-console-namespace-legacy-route-migration.md) | Console 路由骨架 | 为 Admin Console 角色化工作台提供稳定 `/console/*` 路由 |
| P1-07 | [TASK-20260507-006 管理端 UI 信息架构与角色化工作台重整](../../tasks/backlog/TASK-20260507-006-admin-ui-information-architecture-workbench.md) | Admin UI 父级收口 | 与 P1-08 同批推进，作为管理端 IA 的整体验收任务 |
| P1-08 | [TASK-20260507-012 Admin Console 角色化工作台与导航体系](../../tasks/backlog/TASK-20260507-012-admin-console-role-workbench-navigation.md) | Admin UI 具体落地 | 承接 P1-06/P1-07，落地任务卡、导航分组和搜索入口 |
| P1-09 | [TASK-20260507-005 Codex 接入向导、Client Instance 与 Deep Link UI 闭环](../../tasks/backlog/TASK-20260507-005-codex-onboarding-client-instance-deeplink-ui.md) | Codex 接入主路径 | 依赖 P0 权限边界和 P0-04 真实 smoke，提供端到端向导 |
| P1-10 | [TASK-20260507-010 Community Portal Codex 自助接入与个人用量界面](../../tasks/backlog/TASK-20260507-010-community-portal-codex-self-service-surface.md) | 社区用户主路径 | 基于 P1-09，把向导和 usage 收敛到 Portal 用户视角 |
| P2-11 | [TASK-20260507-003 Codex 账号池热切换、负载均衡与失败恢复 UI](../../tasks/backlog/TASK-20260507-003-codex-account-pool-hot-switch-failover-ui.md) | 管理员运营效率 | 依赖 P0-01/P0-04 的请求与账号事实源，补账号池操作面 |
| P2-12 | [TASK-20260507-004 Codex 实时请求、Usage 与过滤命中观测台](../../tasks/backlog/TASK-20260507-004-codex-realtime-usage-filter-observability.md) | 排障与观测效率 | 依赖 session metadata、request log、usage 和 filter 命中事实源 |
| P3-13 | [TASK-20260507-007 前端可用性验收、表单友好性与移动端体验硬化](../../tasks/backlog/TASK-20260507-007-frontend-usability-form-mobile-hardening.md) | 全局体验收口 | 承接 P1-05 的验收矩阵，对核心页面做最终硬化 |

## 并行建议

- `P0-01` 与 `P0-02` 可以并行，但 `P0-03` 应在主要 Portal/Console UI 扩展前完成。
- `P1-06`、`P1-07`、`P1-08` 可作为一个 Admin Console 批次推进。
- `P1-09` 与 `P1-10` 可作为一个 Community Portal/Codex 接入批次推进。
- `P2-11` 与 `P2-12` 可在账号 adapter 和 session metadata 稳定后并行推进。

## 当前建议下一步

下一轮优先闭环 `TASK-20260507-002`。它是 Codex 反代可用性的最底层契约，会影响真实账号 smoke、session 粘性、Nginx 部署、request log、trace、observability 和后续 UI 解释。
