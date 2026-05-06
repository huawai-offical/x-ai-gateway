# TASK-20260505-002 Linear 全量历史任务归档

状态：Done
优先级：High
来源：User Request
关联需求：[REQ-20260505-001](../../docs/requirements/REQ-20260505-001-notion-linear-back-migration.md)
关联迁移记录：[MIG-20260505](../../docs/migrations/MIG-20260505-notion-linear-back-migration.md)
关联归档：[LINEAR-20260505](../../docs/migrations/LINEAR-20260505-x-ai-gateway-issue-archive.md)

## 背景

用户补充要求：Linear 上即使已经完结的任务也需要回迁到本地。本任务用于记录全量历史任务回迁本身，不再只迁移待办项。

## 目标

- 从 Linear 项目 `x-ai-gateway V1 多协议 AI 网关` 读取所有当前可见 issue。
- 覆盖 `Done`、`Duplicate/canceled`、`Backlog` 等状态。
- 将历史任务清单落到本地 Markdown，避免线上 Linear 成为唯一事实来源。

## 实现结果

- 已通过 Linear 项目级分页读取到 `180` 条 issue，直到 `hasNextPage=false`。
- 已将全量 issue 清单写入 [LINEAR-20260505](../../docs/migrations/LINEAR-20260505-x-ai-gateway-issue-archive.md)。
- 已确认其中 `164` 条为 `Done`，`15` 条为 `Duplicate/canceled`，`1` 条为 `Backlog`。
- 唯一仍需继续跟踪的 `X-263` 已拆成本地 backlog 任务：[TASK-20260505-003](../backlog/TASK-20260505-003-linear-x263-second-gap-overview.md)。

## 测试/验证

- Linear 项目查询成功。
- 分页读取结束条件为 `hasNextPage=false`。
- 本地归档包含所有读取到的 issue ID。

## 遗留问题

- 本任务不将 180 条历史 issue 拆成 180 个独立本地 task 文件，避免污染当前 backlog/done 目录；历史事实统一保存在归档表中。
- 后续若某个 Done issue 需要复盘或重开，可按归档 ID 单独拆出任务文件。

## 后续建议

- 当前项目后续默认不再依赖线上 Linear 跟踪。
- 新需求优先创建本地 `tasks/backlog/` 文件；必要时再同步线上系统。
