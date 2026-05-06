# REQ-20260501-001 本地化协作流程迁移

状态：Done  
日期：2026-05-01  
关联决策：[ADR-0001](../decisions/ADR-0001-local-docs-tasks-workflow.md)  
关联迁移：[MIG-20260501](../migrations/MIG-20260501-notion-linear-to-local.md)  
关联任务：[tasks/index.md](../../tasks/index.md)

## 背景

此前项目协作规则要求任何需求在编码前必须先创建 Notion 文档和 Linear 任务。实际使用中，Linear 免费计划触发 issue 数量限制，导致部分增强任务无法创建；同时线上 Notion/Linear 会让项目关键上下文分散在仓库之外。

用户明确要求：

- 修改 `AGENTS.md`，采用本地化方案。
- 在项目内创建 `docs/` 和 `tasks/` 文件夹。
- 将已经沉淀到线上 Notion/Linear 的数据全部迁移到本地。

## 目标

- 将本仓库协作流程切换为本地优先。
- 使用仓库内 Markdown 文件替代线上 Notion 文档。
- 使用仓库内 task 文件替代线上 Linear issue。
- 将本次对标分析、Linear 父任务、子任务、评论和未创建任务迁移到本地。

## 范围

- 新增或更新 `AGENTS.md`。
- 补充 `docs/index.md`、需求文档、决策记录、迁移记录和差距分析报告。
- 创建 `tasks/` 目录结构、任务模板、任务索引。
- 将 X-281 到 X-289 以及未能创建的 4 个任务迁移为本地 backlog task。

## 非目标

- 不删除线上 Notion 页面。
- 不删除或归档线上 Linear issue。
- 不改动业务代码、测试代码或前端实现。
- 不引入新的项目管理服务。

## 风险

- 线上数据后续如果继续被修改，本地迁移结果不会自动同步。
- 本地 Markdown 任务依赖人工维护状态，需要在开发流程中持续执行。
- 如果多人协作，需要通过 Git 分支和 Review 处理文档冲突。

## 验收标准

- 仓库根目录存在本地化版 `AGENTS.md`。
- `docs/` 下存在需求、决策、报告、迁移记录和索引。
- `tasks/` 下存在 backlog、in-progress、done、templates 和索引。
- 线上 Notion/Linear 本轮数据已经能在本地文件中检索到。
- 后续流程不再默认要求线上 Notion/Linear。

## 实现结果

- 已新增本地化协作规则。
- 已将线上差距分析迁移为本地报告。
- 已将 Linear 父任务、8 个子任务、2 条评论和 4 个未创建任务迁移为本地任务与迁移记录。
- 已将对应线上 Notion 页面替换为已迁移占位说明。
- 已将对应 Linear X-281 到 X-289 替换为已迁移占位描述并取消线上跟踪。
- 已删除迁移过程中追加在 X-281 的两条 Linear 评论。
- 已更新全局 `C:/Users/zzp84/.codex/AGENTS.md`，取消 Notion/Linear 强制流程，改为本地优先流程。

## 测试/验证

- 通过文件检查确认本地目录与 Markdown 文件已创建。
- 本次未运行业务测试，因为没有改动应用代码。

## 遗留问题

- 线上 Notion/Linear 仍保留历史数据，后续是否归档由用户决定。
- 如果需要 UI 化任务看板，可在本地 Markdown 稳定后再评估 Plane、Gitea、Forgejo 或 Obsidian。
- 当前连接器没有暴露 Notion 页面物理删除和 Linear issue 物理删除接口，因此本次线上清理采用占位/取消状态，不是物理删除。
