# TASK-20260507-015 当前 Backlog 优先级队列编排

状态：Done  
优先级：High  
来源：User Request  
关联报告：[REP-20260507 当前 Backlog 优先级队列](../../docs/reports/REP-20260507-backlog-priority-roadmap.md)

## 背景

当前 backlog 包含 13 个 Codex 反代、Portal/Admin 区分和 UI/UX 相关任务。它们已有 High/Medium 粗粒度优先级，但缺少可执行的全局顺序和依赖说明。

## 目标

- 给所有当前 backlog 任务排出明确队列。
- 将队列写入任务索引和各 backlog 任务文件。
- 明确哪些任务是地基、哪些是主路径、哪些是运营效率、哪些是体验收口。

## 实现结果

- 新增 [REP-20260507 当前 Backlog 优先级队列](../../docs/reports/REP-20260507-backlog-priority-roadmap.md)。
- 为 13 个 backlog 任务补充 `排期` 字段：`P0-01` 到 `P3-13`。
- 更新 [tasks/index.md](../index.md)，增加“当前 Backlog 优先级队列”。
- 更新 [docs/index.md](../../docs/index.md)，加入优先级队列报告。

## 验收标准

- 所有当前 backlog 任务都有明确排期。
- `tasks/index.md` 能直接看到全局执行顺序。
- 后续可以从 `TASK-20260507-002` 开始按顺序推进。

## 验证

- 本轮进行 Markdown 与任务索引更新。
- 已执行 `git diff --check -- docs\reports\REP-20260507-backlog-priority-roadmap.md tasks\done\TASK-20260507-015-backlog-priority-roadmap.md docs\index.md tasks\index.md tasks\backlog\TASK-20260507-001-codex-official-account-real-adapter-smoke.md tasks\backlog\TASK-20260507-002-codex-cli-request-fidelity-sticky-session.md tasks\backlog\TASK-20260507-003-codex-account-pool-hot-switch-failover-ui.md tasks\backlog\TASK-20260507-004-codex-realtime-usage-filter-observability.md tasks\backlog\TASK-20260507-005-codex-onboarding-client-instance-deeplink-ui.md tasks\backlog\TASK-20260507-006-admin-ui-information-architecture-workbench.md tasks\backlog\TASK-20260507-007-frontend-usability-form-mobile-hardening.md tasks\backlog\TASK-20260507-009-portal-admin-route-identity-boundary.md tasks\backlog\TASK-20260507-010-community-portal-codex-self-service-surface.md tasks\backlog\TASK-20260507-011-admin-console-namespace-legacy-route-migration.md tasks\backlog\TASK-20260507-012-admin-console-role-workbench-navigation.md tasks\backlog\TASK-20260507-013-portal-admin-permission-audit-regression.md tasks\backlog\TASK-20260507-014-portal-console-ux-acceptance-system.md`。
- 校验结果：仅 `docs/index.md` 与 `tasks/index.md` 存在 Git LF/CRLF 提示，无 whitespace error。

## 遗留问题

- 本任务只做优先级编排，不进入代码实现。
- 若后续新增 backlog，需要继续补入队列并重新校准顺序。
