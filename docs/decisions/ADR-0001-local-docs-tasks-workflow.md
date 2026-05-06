# ADR-0001 使用仓库内 Markdown 替代线上 Notion/Linear

状态：Accepted  
日期：2026-05-01  
关联需求：[REQ-20260501-001](../requirements/REQ-20260501-001-local-workflow-migration.md)

## 背景

项目早期使用线上 Notion 记录需求与方案，使用线上 Linear 管理任务。该方式可以提供 UI 和协作能力，但也带来几个问题：

- 数据与代码分离，关键上下文不随仓库版本化。
- Linear Free 计划存在 issue 数量限制，本次已经阻塞 4 个增强任务创建。
- 离线或权限受限时，开发流程会被 SaaS 可用性影响。
- Codex 在实现前需要先触达外部系统，增加流程成本。

## 决策

从 2026-05-01 起，本仓库默认采用本地优先协作流程：

- `docs/` 作为需求、方案、报告、决策和迁移记录的事实来源。
- `tasks/` 作为任务拆分、状态、验收和交付记录的事实来源。
- 线上 Notion/Linear 只在用户明确要求时使用。
- 本地文档和任务必须随代码一起进入 Git 版本管理。

## 影响

正向影响：

- 不再受 Linear issue 限额阻塞。
- 文档、任务和代码可以在同一 PR 中 Review。
- 历史决策可通过 Git 追溯。
- 离线环境仍可工作。

代价：

- 缺少 Linear/Notion 的可视化看板和提醒。
- 状态流转需要通过文件移动或索引维护。
- 多人同时编辑文档时可能出现 Git 冲突。

## 后续约定

- 新需求先写 `docs/requirements/REQ-*.md`。
- 技术选型或流程变化写 `docs/decisions/ADR-*.md`。
- 调研和对标结果写 `docs/reports/REP-*.md`。
- 迁移外部资料写 `docs/migrations/MIG-*.md`。
- 开发任务写 `tasks/backlog/TASK-*.md`，开始后移动到 `tasks/in-progress/`，完成后移动到 `tasks/done/`。

