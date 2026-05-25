# TASK-20260525-001-01 Admin 邀请树前端接入

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260525-001](TASK-20260525-001-admin-invitation-tree-ui-parent.md)  
上游来源：[REQ-20260525-001](../../docs/requirements/REQ-20260525-001-admin-invitation-tree-ui.md)

## 背景

Admin 邀请码页面已有邀请码列表、奖励配置、使用记录和排行榜。后端已经提供邀请树 API，但页面没有查询入口和树形展示。

## 目标

- 在 `invitation-codes-page.tsx` 中新增 `InvitationTreeNode` 类型。
- 新增根用户 ID、最大深度和已提交查询状态。
- 接入 `/admin/invitation-codes/tree/{userId}?maxDepth=...`。
- 新增树形节点递归组件，展示节点身份、层级、邀请时间和子节点数量。
- 排行榜每行提供查看树操作。

## 非目标

- 不拆分页面大文件。
- 不新增后端字段。
- 不实现用户 picker。

## 输入

- `web/src/features/user-domain/invitation-codes-page.tsx`
- `InvitationTreeNodeResponse`

## 输出

- Admin 邀请树 UI。
- `bun run typecheck` 验证。

## 影响范围

- `web/src/features/user-domain/invitation-codes-page.tsx`

## 依赖

- 后端树接口返回 `userId`、`email`、`displayName`、`depth`、`invitedAt`、`children`。

## 风险

- 递归组件需限制文本换行和缩进，避免表格区域和移动端布局挤压。
- 手动输入需要正整数校验，避免发起明显无效请求。

## 验收标准

- 手动查询和排行榜一键查询均可设置根用户并加载树。
- Loading、error、empty 和有数据状态均有明确 UI。
- 前端类型检查通过。

## 测试边界

- `cd web; bun run typecheck`。
- 可选浏览器交互验证。

## 关联文档

- [REQ-20260525-001](../../docs/requirements/REQ-20260525-001-admin-invitation-tree-ui.md)

## 当前状态

- 2026-05-25：子任务创建，待实现。
- 2026-05-25：已实现并通过验证，准备归档。

## 实现结果

- `InvitationCodesPage` 新增邀请树查询状态和 `/admin/invitation-codes/tree/{userId}?maxDepth=...` 查询。
- 页面新增“邀请树”管理区，支持输入根用户 ID 和最大深度。
- 排行榜新增“邀请树”按钮，可直接查询对应用户的邀请层级。
- 新增 `InvitationTreeView`、`TreeChildren`、`TreeNodeCard` 等轻量递归展示组件。
- 新增 focused 前端测试覆盖手动查询和排行榜入口。

## 测试/验证

- `cd web; bun run typecheck`
- `cd web; bun run test -- src/features/user-domain/invitation-codes-page.test.tsx`

## 遗留问题

- 未新增图谱库或复杂可视化布局。
- 浏览器验证受后端登录 challenge API 不可用限制，未完成登录后真页面交互。
