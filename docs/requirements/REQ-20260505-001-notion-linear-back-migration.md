# REQ-20260505-001 Notion/Linear 线上数据回迁到本地

状态：Done
创建日期：2026-05-05
关联迁移记录：[MIG-20260505 Notion/Linear 回迁审计与本地落地](../migrations/MIG-20260505-notion-linear-back-migration.md)
关联任务：[TASK-20260505-001 Notion/Linear 回迁到本地](../../tasks/done/TASK-20260505-001-notion-linear-back-migration.md)

## 背景

仓库已建立本地优先协作流程，并在 2026-05-01 完成过一次 Notion/Linear 到本地 Markdown 的迁移和线上占位清理。用户本次明确要求再次回迁 Notion 文档和 Linear 任务，因此允许读取线上连接器，并将当前线上可见数据再次落地到仓库内。

## 目标

- 读取线上 Notion 中与 `x-ai-gateway` 相关的当前可见文档。
- 读取线上 Linear 中与 `x-ai-gateway` 项目相关的当前可见任务，包括已完成、已取消和待办任务。
- 将新增或仍有价值的线上内容迁移到本地 Markdown。
- 更新迁移记录、文档索引和任务索引，明确本地文件是事实来源。

## 范围

- Notion 搜索、页面获取、内容摘要与本地文档落地。
- Linear issue 搜索/读取、任务字段与状态摘要回迁。
- 本地迁移记录写入 `docs/migrations/`。
- 本地任务文件写入 `tasks/backlog/`、`tasks/in-progress/` 或 `tasks/done/`。

## 非目标

- 本次不创建新的线上 Notion 页面。
- 本次不创建或更新线上 Linear issue。
- 本次不做业务功能代码修改。
- 本次不物理删除线上数据；若线上连接器仅支持读取，则只记录当前线上状态。

## 风险

- 线上连接器可能没有权限、已断开、或返回被清理后的占位内容。
- Linear 免费 issue 限额可能影响创建能力；本次已确认读取不受阻断。
- 线上数据可能已经在 2026-05-01 被占位清理，无法再获得原始正文。

## 验收标准

- 形成新的迁移记录，列出 Notion/Linear 的查询范围、来源、结果和本地落点。
- 若发现新内容，生成或更新对应本地文档/任务。
- 若未发现新内容，也要在迁移记录中明确“线上已为占位/未发现新增内容”。
- `docs/index.md` 与 `tasks/index.md` 保持可追溯。

## 实现结果

- 已读取 Notion `x-ai-gateway` 相关页面，并形成本地摘要：[REP-20260505](../reports/REP-20260505-notion-back-migration-summary.md)。
- 已读取 Linear 项目 `x-ai-gateway V1 多协议 AI 网关` 的全部当前可见 issue，共 `180` 条。
- 已将 Linear 全量 issue 清单落地为：[LINEAR-20260505](../migrations/LINEAR-20260505-x-ai-gateway-issue-archive.md)。
- 已新增历史归档任务：[TASK-20260505-002](../../tasks/done/TASK-20260505-002-linear-all-issue-history-archive.md)。
- 已将唯一仍为 Backlog 的 X-263 拆成本地任务：[TASK-20260505-003](../../tasks/backlog/TASK-20260505-003-linear-x263-second-gap-overview.md)。

## 验证情况

- Notion 搜索与 fetch 成功。
- Linear 项目级分页读取成功，最终返回 `hasNextPage=false`。
- 本地归档统计：`Done` 164 条，`Duplicate/canceled` 15 条，`Backlog` 1 条。

## 遗留问题

- 未执行线上 Notion/Linear 删除或清空。
- Notion 长文页本轮以摘要方式回迁，未逐页全文拆分。

## 后续建议

- 后续若需要复盘某个 Linear Done issue，可从 [LINEAR-20260505](../migrations/LINEAR-20260505-x-ai-gateway-issue-archive.md) 按 ID 单独拆出任务。
- X-263 后续应先做代码态审计，再拆分剩余未闭环项。
