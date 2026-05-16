# TASK-20260508-007 文档状态与任务链接一致性清理

状态：Done  
优先级：Medium  
来源：REQ-20260508-003  
创建日期：2026-05-08

## 背景

用户要求清理和修正文档。当前仓库任务索引显示所有任务已归档，但部分需求文档仍残留非 Done 状态或旧 backlog 链接，需要收口为一致的本地事实源。

## 目标

- 修正历史需求文档中的状态残留。
- 修正已完成任务的旧 backlog 链接。
- 更新文档索引和任务索引，保留本轮清理记录。
- 执行文本检索校验，避免遗留旧链接导致后续误判。

## 范围

- `docs/requirements/` 下需求文档。
- `docs/index.md`。
- `tasks/index.md`。

## 验收标准

- `docs/requirements/` 中不再有指向旧 backlog 位置的已归档任务链接。
- `REQ-20260505-002` 与 `REQ-20260507-001` 状态修正为 `Done`。
- 本任务完成后移动到 `tasks/done/` 并回写验证情况。

## 实现结果

- 新增并回写 `REQ-20260508-003` 作为本轮文档清理需求记录。
- 修正历史需求文档中的非 Done 状态残留。
- 修正历史需求文档中已归档任务仍指向 `tasks/backlog/` 的链接。
- 更新 `docs/index.md` 与 `tasks/index.md`，本任务已归档到 `tasks/done/`。

## 验证情况

- 已检索 `docs/requirements/*.md`，未发现旧 backlog 任务链接。
- 已检索 `tasks/backlog/` 与 `tasks/in-progress/`，未发现 `.gitkeep` 以外的任务文件。
- 已检索 `tasks/index.md`，未发现 `Backlog` 或 `In Progress` 任务行。

## 遗留问题

- 无业务代码变更；未运行后端或前端测试。
