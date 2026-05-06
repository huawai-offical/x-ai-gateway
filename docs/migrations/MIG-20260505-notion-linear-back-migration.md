# MIG-20260505 Notion/Linear 回迁审计与本地落地

状态：Done
日期：2026-05-05
关联需求：[REQ-20260505-001](../requirements/REQ-20260505-001-notion-linear-back-migration.md)
关联任务：[TASK-20260505-001](../../tasks/done/TASK-20260505-001-notion-linear-back-migration.md)

## 迁移目标

根据用户要求，重新读取线上 Notion 文档与 Linear 任务，将当前线上可见内容回迁到本地仓库。

## 查询范围

### Notion

- 搜索关键词：`x-ai-gateway`
- 搜索关键词：`对标 new-api Sub2API CC Switch`
- 搜索关键词：`已迁移到本地 x-ai-gateway`
- 对相关页面执行 Notion fetch，读取正文和元数据。

### Linear

- 读取项目：`x-ai-gateway V1 多协议 AI 网关`
- 项目 ID：`a265a53a-e605-46ae-ab98-6f68268b951e`
- 读取范围：所有当前可见 issue，包括 `Done`、`Duplicate/canceled`、`Backlog`。
- 单独复核历史 issue：`X-281` 至 `X-289`。

## 本地落点

- Notion 摘要报告：[REP-20260505](../reports/REP-20260505-notion-back-migration-summary.md)
- Linear 全量归档：[LINEAR-20260505](LINEAR-20260505-x-ai-gateway-issue-archive.md)
- Linear 历史归档任务：[TASK-20260505-002](../../tasks/done/TASK-20260505-002-linear-all-issue-history-archive.md)
- Linear X-263 本地 backlog：[TASK-20260505-003](../../tasks/backlog/TASK-20260505-003-linear-x263-second-gap-overview.md)

## Notion 回迁结果

- 成功读取 `x-ai-gateway` 相关 Notion 页面。
- 已确认 2026-05-01 的对标分析页仍为“已迁移到本地”占位页，原文以 [REP-20260501](../reports/REP-20260501-open-source-gap-analysis.md) 为准。
- 已回迁 Notion 页面树、WBS、Spring AI 边界、全厂商自动翻译闭环、协议无感自动路由 V2、ai-gateway 差距审计等关键摘要。
- 本轮未删除或清空 Notion 线上页面。

## Linear 回迁结果

- Linear 项目级读取成功。
- 分页读取到 `180` 条 issue，最终 `hasNextPage=false`。
- 状态统计：
  - `Done / completed`：164 条
  - `Duplicate / canceled`：15 条
  - `Backlog / backlog`：1 条
- `X-281` 至 `X-289` 均仍为已迁移占位 issue，状态为 `Duplicate/canceled`。
- `X-263` 是本轮唯一仍需本地继续跟踪的 Backlog 项。

## 差异与新增内容

- 与 2026-05-01 迁移相比，本轮新增了 Linear 项目全量历史归档，不再只覆盖 X-281 至 X-289。
- 已完成的 Linear issue 也被迁移进本地归档，避免历史闭环只存在于线上。
- X-263 已作为继续跟踪项拆成本地 backlog 任务。

## 线上清理/保留状态

- Notion：本轮只读取和摘要回迁，未执行线上清理。
- Linear：本轮只读取和归档，未创建、更新、删除线上 issue。
- 后续事实来源：本地 `docs/` 与 `tasks/`。

## 验证

- Notion 搜索与 fetch 成功。
- Linear 项目查询成功，分页读到尾页。
- 本地新增文件：
  - `docs/reports/REP-20260505-notion-back-migration-summary.md`
  - `docs/migrations/LINEAR-20260505-x-ai-gateway-issue-archive.md`
  - `tasks/done/TASK-20260505-002-linear-all-issue-history-archive.md`
  - `tasks/backlog/TASK-20260505-003-linear-x263-second-gap-overview.md`
