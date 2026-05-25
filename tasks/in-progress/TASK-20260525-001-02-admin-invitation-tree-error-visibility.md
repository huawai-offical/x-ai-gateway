# TASK-20260525-001-02 Admin 邀请树错误态可见化

状态：In Progress  
优先级：High  
类型：子任务  
父任务：[TASK-20260525-001](../done/TASK-20260525-001-admin-invitation-tree-ui-parent.md)  
上游来源：[REQ-20260525-001](../../docs/requirements/REQ-20260525-001-admin-invitation-tree-ui.md)

## 背景

真实后端和前端启动后补测 Admin 邀请树时，`/admin/invitation-codes/tree/{userId}` 对不存在用户会返回 `400 INVALID_ARGUMENT` 与“未找到指定用户。”。当前页面调用 `InlineError` 后只触发 toast，不在邀请树区域渲染可见错误内容，导致管理员在 toast 消失或未注意到 toast 时看到空白区域，容易误判为没有查询结果。

## 目标

- 邀请树查询失败时，在邀请树区域显示可见错误块。
- 错误块展示错误标题、错误消息和可选 traceId。
- 保留现有 toast 行为，不改动全局 `InlineError` 语义。
- 补充 focused 前端测试覆盖查询失败可见错误。

## 非目标

- 不改后端错误码和接口契约。
- 不调整 React Query 全局重试策略。
- 不重构全局错误组件。
- 不新增图谱或用户 picker。

## 输入

- `web/src/features/user-domain/invitation-codes-page.tsx`
- `web/src/features/user-domain/invitation-codes-page.test.tsx`
- 真实补测结果：不存在用户查询返回 `INVALID_ARGUMENT / 未找到指定用户。`

## 输出

- 邀请树区域可见错误态。
- focused 前端测试。
- 需求和任务验证记录回写。

## 影响范围

- `web/src/features/user-domain/invitation-codes-page.tsx`
- `web/src/features/user-domain/invitation-codes-page.test.tsx`
- `docs/requirements/REQ-20260525-001-admin-invitation-tree-ui.md`
- `tasks/done/TASK-20260525-001-admin-invitation-tree-ui-parent.md`
- 本任务文件。

## 依赖

- 现有 `InlineError` 继续负责 toast。
- 现有 `ApiError` / `Error` 消息结构。

## 风险

- 如果直接修改全局 `InlineError`，可能让其他页面重复渲染错误内容；本任务只在邀请树局部增加可见错误块。
- React Query 对失败查询可能重试，测试需禁用重试并验证最终错误态。

## 验收标准

- 手动查询不存在用户时，页面邀请树区域显示“邀请树加载失败”和具体错误消息。
- 表单校验失败时，页面邀请树区域显示“邀请树查询失败”和校验消息。
- 成功查询、排行榜入口和空态不回退。
- `cd web; bun run test -- src/features/user-domain/invitation-codes-page.test.tsx` 通过。
- `cd web; bun run typecheck` 通过。

## 测试边界

- 前端 focused test 覆盖成功查询、排行榜入口和错误态。
- 浏览器补测记录真实后端成功空子树、排行榜空态和临时数据清理。
- 不执行真实注册、真实社交 OAuth 或真实邀请关系链路。

## 关联文档

- [REQ-20260525-001](../../docs/requirements/REQ-20260525-001-admin-invitation-tree-ui.md)

## 关联任务

- [TASK-20260525-001](../done/TASK-20260525-001-admin-invitation-tree-ui-parent.md)
- [TASK-20260525-001-01](../done/TASK-20260525-001-01-admin-invitation-tree-frontend.md)

## 当前状态

- 2026-05-25：真实补测发现错误态不可见，创建子任务并进入实现。
