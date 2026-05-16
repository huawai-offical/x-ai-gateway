# REQ-20260508-003 文档状态与任务链接一致性清理

状态：Done  
日期：2026-05-08  
关联任务：

- [TASK-20260508-007 文档状态与任务链接一致性清理](../../tasks/done/TASK-20260508-007-doc-status-link-cleanup.md)

## 背景

当前任务索引和任务目录显示 backlog 与 in-progress 已清空，但部分历史需求文档仍保留 `Ready` 或 `In Progress` 状态，且部分任务链接仍指向 `tasks/backlog/`。这些内容容易让后续排期误判为“还有未闭环任务”。

## 目标

- 将已完成需求文档的状态修正为 `Done`。
- 将已归档任务链接从 `tasks/backlog/` 修正为 `tasks/done/`。
- 保留历史背景描述，但补充清理记录，明确当前事实源以任务目录和索引为准。
- 校验 `docs/requirements/` 中不再残留指向旧 backlog 任务文件的链接。

## 范围

- 需求文档状态与链接清理。
- `docs/index.md` 和 `tasks/index.md` 的追踪入口补充。
- 不修改业务代码，不改动数据库迁移，不变更已归档任务正文的历史事实。

## 风险

- 历史文档中有些 `backlog` 字样是流程说明或历史背景，不能机械替换。
- 当前工作区已有大量未提交改动，本轮只处理文档一致性相关文件。

## 验收标准

- 本轮清理任务可在本地任务索引中追踪。
- 已完成需求不再显示 `Ready` 或 `In Progress`。
- 已归档任务链接不再指向 `tasks/backlog/`。
- `tasks/backlog/` 与 `tasks/in-progress/` 不存在实际待处理任务文件。

## 实现结果

- 修正 `REQ-20260505-002` 状态为 `Done`，并将 `TASK-20260505-006` 链接改为 done 路径。
- 修正 `REQ-20260507-001` 状态为 `Done`，并将 `TASK-20260507-009` 至 `TASK-20260507-014` 链接改为 done 路径。
- 修正 `REQ-20260501-005`、`REQ-20260505-001`、`REQ-20260506-012` 中已完成任务仍指向旧 backlog 路径的问题。
- 更新 `docs/index.md` 与 `tasks/index.md`，保留本轮文档清理记录。

## 验证情况

- `docs/requirements/*.md` 中未检出旧 backlog 任务链接。
- `tasks/backlog/` 与 `tasks/in-progress/` 未检出实际任务文件。
- `tasks/index.md` 未检出 `Backlog` 或 `In Progress` 任务行。

## 遗留问题

- 本轮为文档一致性清理，不涉及业务代码与自动化测试。
