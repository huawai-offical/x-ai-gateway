# TASK-20260505-001 Notion/Linear 回迁到本地

状态：Done
优先级：High
来源：User Request
关联需求：[REQ-20260505-001](../../docs/requirements/REQ-20260505-001-notion-linear-back-migration.md)
关联迁移记录：[MIG-20260505](../../docs/migrations/MIG-20260505-notion-linear-back-migration.md)

## 背景

用户明确要求将线上 Notion 文档和 Linear 任务回迁到本地。仓库已有 2026-05-01 的一次迁移记录，本任务用于执行二次回迁审计和增量落地。

## 目标

- 读取线上 Notion 当前可见文档。
- 读取线上 Linear 当前可见任务。
- 将新增或仍有价值的数据落地为本地 Markdown。
- 回写迁移记录和索引。

## 范围

- Notion 搜索和 fetch。
- Linear issue/list/search 读取。
- 本地 Markdown 文档与任务更新。
- 迁移状态、来源链接和差异说明记录。

## 非目标

- 不修改业务代码。
- 不向线上 Notion/Linear 写入新内容。
- 不清空或删除线上数据。

## 风险

- 线上连接器可能不可用。
- 线上内容可能已经在第一次迁移后被占位清理。
- Linear issue 免费限额或权限可能影响读取结果。

## 验收标准

- 迁移记录中明确列出 Notion/Linear 查询结果。
- 本地文档和任务索引更新。
- 若未发现新增内容，也明确记录原因和状态。

## 实现记录

- 已读取线上 Notion `x-ai-gateway` 相关页面，并形成本地摘要：[REP-20260505](../../docs/reports/REP-20260505-notion-back-migration-summary.md)。
- 已通过 Linear 项目级分页读取 `x-ai-gateway V1 多协议 AI 网关` 当前可见全部 issue。
- 已将 Linear `180` 条 issue 全量归档到：[LINEAR-20260505](../../docs/migrations/LINEAR-20260505-x-ai-gateway-issue-archive.md)。
- 已按用户补充要求覆盖已完成任务：`Done` 164 条、`Duplicate/canceled` 15 条、`Backlog` 1 条。
- 已新增历史归档任务：[TASK-20260505-002](TASK-20260505-002-linear-all-issue-history-archive.md)。
- 已将唯一仍为 Backlog 的 X-263 拆成本地任务：[TASK-20260505-003](../backlog/TASK-20260505-003-linear-x263-second-gap-overview.md)。

## 测试/验证

- Notion 搜索与 fetch 成功。
- Linear 项目级分页读取成功，最后一页返回 `hasNextPage=false`。
- 本轮只修改本地 Markdown 文档与任务，不涉及业务代码测试。

## 遗留问题

- 未执行线上 Notion/Linear 删除或清空。
- Notion 长文内容本轮以摘要形式落地，未逐页全文归档。

## 后续建议

- 后续围绕 X-263 做代码态审计，确认 X-264 至 X-280 已闭环后的剩余缺口。
- 如需复盘任一历史 Linear Done issue，可从 [LINEAR-20260505](../../docs/migrations/LINEAR-20260505-x-ai-gateway-issue-archive.md) 按 ID 单独拆任务。
