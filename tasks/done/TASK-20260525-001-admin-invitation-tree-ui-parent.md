# TASK-20260525-001 Admin 邀请树展示补齐父任务

状态：Done  
优先级：High  
类型：父任务  
上游来源：[REQ-20260525-001](../../docs/requirements/REQ-20260525-001-admin-invitation-tree-ui.md)

## 背景

邀请增长系统已经具备邀请关系、排行榜和 Admin 邀请树 API，但 Admin 前端只展示排行榜，没有提供层级邀请树入口。用户要求完善 Admin 邀请树，需要补齐管理侧可视化闭环。

## 目标

- 在 Admin 邀请码页面接入邀请树查询与展示。
- 支持排行榜用户一键查看邀请树。
- 保持现有邀请码管理、奖励配置、使用记录和排行榜行为不回退。
- 完成前端类型检查，并回写需求和任务状态。

## 非目标

- 不修改邀请关系落库事务。
- 不新增数据库迁移。
- 不扩展 Portal 邀请页。
- 不引入第三方图谱组件。

## 输入

- [REQ-20260525-001](../../docs/requirements/REQ-20260525-001-admin-invitation-tree-ui.md)
- `web/src/features/user-domain/invitation-codes-page.tsx`
- `InvitationTreeNodeResponse`
- `InvitationGrowthService.tree(...)`

## 输出

- 子任务 [TASK-20260525-001-01](TASK-20260525-001-01-admin-invitation-tree-frontend.md)
- Admin 邀请树 UI 实现。
- 验证记录与归档状态。

## 影响范围

- `web/src/features/user-domain/invitation-codes-page.tsx`
- `docs/requirements/REQ-20260525-001-admin-invitation-tree-ui.md`
- `docs/index.md`
- `tasks/index.md`
- 本任务与子任务文件。

## 依赖

- 已有 `/admin/invitation-codes/tree/{userId}` API。
- 已有 `/admin/invitation-codes/leaderboard` API。

## 风险

- 页面文件已经较大，新增逻辑需要控制范围，避免制造额外拆分成本。
- 当前工作区有大量既有未提交改动，必须只触碰本任务相关文件。

## 验收标准

- 子任务完成并通过验证。
- 需求文档记录实现结果、验证情况和遗留边界。
- 父任务和子任务移动或记录到 `tasks/done/`。

## 测试边界

- 前端 typecheck 是必需验证。
- 浏览器验证视本地服务启动情况执行。
- 不验证真实注册和 OAuth。

## 关联文档

- [REQ-20260525-001](../../docs/requirements/REQ-20260525-001-admin-invitation-tree-ui.md)
- [REQ-20260524-005](../../docs/requirements/REQ-20260524-005-invitation-growth-system.md)

## 关联任务

- [TASK-20260524-005](../done/TASK-20260524-005-invitation-growth-system-parent.md)
- [TASK-20260525-001-01](TASK-20260525-001-01-admin-invitation-tree-frontend.md)

## 当前状态

- 2026-05-25：父任务创建，进入实现。
- 2026-05-25：子任务完成，focused test 与 typecheck 通过；父任务准备归档。

## 实现结果

- 完成 [TASK-20260525-001-01](TASK-20260525-001-01-admin-invitation-tree-frontend.md)。
- Admin 邀请码页面新增邀请树查询、排行榜一键查看和递归树形展示。
- 需求文档已回写实现结果、验证记录和遗留边界。

## 测试/验证

- `cd web; bun run typecheck`
- `cd web; bun run test -- src/features/user-domain/invitation-codes-page.test.tsx`

## 遗留问题

- 后端登录 challenge API 当前不可用，浏览器只能验证到登录页和服务不可用错误，未完成真实登录后的页面交互。
