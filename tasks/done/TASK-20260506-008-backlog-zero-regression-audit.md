# TASK-20260506-008 剩余任务清零回归审计

状态：Done  
优先级：High  
来源：User Request  
关联需求：[REQ-20260506-006](../../docs/requirements/REQ-20260506-006-backlog-zero-regression-audit.md)

## 背景

当前任务索引显示所有增强任务已经归档 Done，用户要求继续推进项目并闭环剩余任务。本任务用于确认本地任务清零状态，并通过后端/前端关键验证确认项目没有明显未闭环项。

## 目标

- 确认 backlog 与 in-progress 清零。
- 运行后端和前端验证。
- 对发现的问题做修复或记录。
- 回写本地审计结果。

## 范围

- `tasks/index.md` 状态核对。
- `tasks/backlog/` 与 `tasks/in-progress/` 文件核对。
- 后端 Gradle 测试。
- 前端 typecheck 与测试。
- 本轮相关文件 `git diff --check`。

## 非目标

- 不引入新的产品增强范围。
- 不清理无关工作区改动。

## 验收标准

- 任务目录无待办任务文件。
- 验证命令已执行并记录结果。
- 审计结论已写入需求文档。

## 实现记录

已完成剩余任务清零审计：

- `tasks/backlog/` 无任务文件。
- `tasks/in-progress/` 归档前仅剩本审计任务和 `.gitkeep`。
- 已修正部分已完成需求文档中指向 `tasks/in-progress/` 的过期任务链接。
- 未发现仍需补充的本地增强任务。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks
bun run typecheck
bun run test
```

补充检查：

```powershell
git diff --check -- docs/index.md docs/requirements/REQ-20260506-006-backlog-zero-regression-audit.md tasks/index.md tasks/in-progress/TASK-20260506-008-backlog-zero-regression-audit.md
```

无 diff check 输出。

## 遗留问题

前端测试中存在 jsdom/Recharts 尺寸警告和 React Router HydrateFallback 提示，但不影响测试通过。工作区大量历史未提交改动不在本轮清理范围内。
