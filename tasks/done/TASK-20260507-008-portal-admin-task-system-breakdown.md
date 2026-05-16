# TASK-20260507-008 Portal/Admin 角色化界面任务体系拆分

状态：Done  
优先级：High  
来源：User Request  
关联需求：[REQ-20260507-001 社区 Portal 与后台 Console 角色化界面任务体系](../../docs/requirements/REQ-20260507-001-portal-admin-role-surface-task-system.md)

## 背景

用户追问社区用户使用界面和后台管理员使用界面如何区分，并要求将设计想法拆分成明确任务体系。现有 backlog 已覆盖 Codex 反代、接入向导、管理端信息架构和前端可用性，但还缺少一层“产品面、角色、权限、执行顺序”的组织结构。

## 目标

- 固化 Public Site、Community Portal、Admin Console 三个产品面。
- 将 Portal/Admin 区分拆成可执行 backlog。
- 把既有 `TASK-20260507-005/006/007` 纳入同一体系，避免重复建设。
- 更新本地文档与任务索引，保证后续推进可追踪。

## 实现结果

- 新增 [REQ-20260507-001](../../docs/requirements/REQ-20260507-001-portal-admin-role-surface-task-system.md)，记录背景、目标、产品面定义、权限矩阵、任务体系、执行顺序、风险和验收标准。
- 新增 6 个 backlog 子任务：
  - [TASK-20260507-009 Portal/Admin 路由、身份与产品面边界基线](../backlog/TASK-20260507-009-portal-admin-route-identity-boundary.md)
  - [TASK-20260507-010 Community Portal Codex 自助接入与个人用量界面](../backlog/TASK-20260507-010-community-portal-codex-self-service-surface.md)
  - [TASK-20260507-011 Admin Console 命名空间迁移与旧路由兼容](../backlog/TASK-20260507-011-admin-console-namespace-legacy-route-migration.md)
  - [TASK-20260507-012 Admin Console 角色化工作台与导航体系](../backlog/TASK-20260507-012-admin-console-role-workbench-navigation.md)
  - [TASK-20260507-013 Portal/Admin API 权限隔离、审计与越权回归](../backlog/TASK-20260507-013-portal-admin-permission-audit-regression.md)
  - [TASK-20260507-014 Portal/Console UI/UX 验收体系与组件硬化](../backlog/TASK-20260507-014-portal-console-ux-acceptance-system.md)
- 更新 `docs/index.md` 和 `tasks/index.md`，将任务体系纳入本地索引。

## 验收标准

- 任务体系能清楚区分社区用户界面、后台管理员界面和公开页面。
- 每个新增任务都有明确目标、范围、详细设计、验收标准和风险。
- 后续可按边界基线、Portal 自助、Console 运营、权限回归、UI/UX 验收逐步闭环。

## 验证

- 本轮进行 Markdown 结构化拆分与索引更新。
- 已执行 `git diff --check -- docs\requirements\REQ-20260507-001-portal-admin-role-surface-task-system.md tasks\done\TASK-20260507-008-portal-admin-task-system-breakdown.md tasks\backlog\TASK-20260507-009-portal-admin-route-identity-boundary.md tasks\backlog\TASK-20260507-010-community-portal-codex-self-service-surface.md tasks\backlog\TASK-20260507-011-admin-console-namespace-legacy-route-migration.md tasks\backlog\TASK-20260507-012-admin-console-role-workbench-navigation.md tasks\backlog\TASK-20260507-013-portal-admin-permission-audit-regression.md tasks\backlog\TASK-20260507-014-portal-console-ux-acceptance-system.md docs\index.md tasks\index.md`。
- 校验结果：仅 `docs/index.md` 与 `tasks/index.md` 存在 Git LF/CRLF 提示，无 whitespace error。

## 遗留问题

- 本任务只完成体系拆分，不进入前端或后端实现。
- 具体实现将在新增 backlog 与既有 `TASK-20260507-005/006/007` 中逐项推进。
