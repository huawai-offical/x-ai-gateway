# REQ-20260506-006 剩余任务清零回归审计

状态：Done  
日期：2026-05-06  
关联任务：

- [TASK-20260506-008 剩余任务清零回归审计](../../tasks/done/TASK-20260506-008-backlog-zero-regression-audit.md)

## 背景

当前 `tasks/index.md` 中所有已登记增强任务均为 Done，`tasks/backlog/` 为空，`tasks/in-progress/` 仅保留 `.gitkeep`。用户要求继续推进项目并闭环剩余任务，因此本轮不再新增业务功能，而是对“剩余任务是否清零、已闭环能力是否能通过全量/关键回归验证”做一次本地审计。

## 目标

- 确认本地任务索引、Backlog、In Progress 与 Done 状态一致。
- 运行后端与前端关键回归验证，发现真实失败则修复并回写。
- 将验证结果沉淀到本地文档和任务，形成可追溯的项目收尾记录。

## 范围

- 检查 `tasks/index.md`、`tasks/backlog/`、`tasks/in-progress/`。
- 运行后端 Gradle 测试。
- 运行前端 typecheck 与测试。
- 执行本轮相关文件的 `git diff --check`。
- 回写验证结果和遗留风险。

## 非目标

- 不再拆新的产品增强项，除非验证暴露真实问题。
- 不处理工作区中与本轮无关的既有未提交改动。
- 不接入线上 Linear 或 Notion。

## 方案

1. 创建本地审计任务并标记 In Progress。
2. 运行后端、前端验证命令。
3. 若失败，先定位并修复本轮范围内真实问题。
4. 回写文档与任务，归档到 Done。

## 风险

- 工作区存在大量既有未提交文件，全量 diff 很嘈杂；本轮只针对任务清零与验证结果给出结论。
- 全量测试可能暴露历史任务引入但未在本轮触碰的失败；若出现，会优先区分本轮问题和既有问题。

## 验收标准

- `tasks/backlog/` 无任务文件。
- `tasks/in-progress/` 无任务文件。
- 后端测试通过或失败原因已明确记录。
- 前端 typecheck/test 通过或失败原因已明确记录。
- 本审计任务移动到 `tasks/done/`，并更新 `tasks/index.md`。

## 实现结果

- 已确认 `tasks/backlog/` 无任务文件。
- 已确认 `tasks/in-progress/` 除审计任务归档前的本任务和 `.gitkeep` 外无其他任务文件；归档后仅保留 `.gitkeep`。
- 已修正 `REQ-20260506-004` 与 `REQ-20260506-005` 中已完成任务仍指向 `tasks/in-progress/` 的本地链接。
- 未发现需要新增产品增强任务的剩余 backlog。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --rerun-tasks
bun run typecheck
bun run test
```

验证结果：

- 后端 Gradle 全量测试通过。
- 前端 TypeScript project build/typecheck 通过。
- 前端 Vitest 通过，48 个测试文件、90 个测试用例全部通过。
- 本轮审计文件 `git diff --check` 无输出。

## 遗留问题

- 前端测试输出中仍存在测试环境噪音：React Router `HydrateFallback` 提示，以及 Recharts 在 jsdom 中容器宽高为 0 的警告。它们未导致测试失败，后续若要清理控制台噪音可单独拆任务处理。
- 工作区仍有大量历史未提交改动，本轮没有清理无关文件。
