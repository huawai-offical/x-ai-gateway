# REQ-20260525-001 Admin 邀请树展示补齐

状态：Done  
日期：2026-05-25  
关联任务：[TASK-20260525-001](../../tasks/done/TASK-20260525-001-admin-invitation-tree-ui-parent.md)

## 背景

REQ-20260524-005 已完成邀请码增长系统的后端关系模型、Admin 排行榜 API、Admin 邀请树 API 和 Portal 邀请统计页。交付时明确遗留：Admin 邀请树接口 `/admin/invitation-codes/tree/{userId}` 已存在，但前端没有展示入口。

当前用户要求“完善 Admin 邀请树”，需要把已有树接口接入 Admin 邀请码页面，让管理员可以从排行榜或手动输入用户 ID 查看层级邀请关系，补齐增长系统的管理闭环。

## 目标

- Admin 邀请码页面新增邀请树查询区。
- 管理员可以输入根用户 ID 和最大深度查询邀请树。
- 管理员可以从排行榜用户一键查看对应邀请树。
- 邀请树展示用户 ID、邮箱、显示名、层级、邀请时间和子节点数量。
- 邀请树具备加载态、错误态、空态和深度限制提示。

## 非目标

- 不新增后端邀请关系模型或迁移。
- 不实现图数据库、拖拽图谱、反作弊或财务结算视图。
- 不把 `ownerUserId` 数字输入升级为用户 picker。
- 不新增新的图表库。

## 输入

- `GET /admin/invitation-codes/tree/{userId}?maxDepth=...`
- `GET /admin/invitation-codes/leaderboard?limit=20`
- `web/src/features/user-domain/invitation-codes-page.tsx`
- [REQ-20260524-005](REQ-20260524-005-invitation-growth-system.md)

## 输出

- Admin 邀请码页面上的邀请树 UI。
- 前端类型定义和查询逻辑。
- `bun run typecheck` 验证记录。
- 本地文档与任务状态回写。

## 影响范围

- `web/src/features/user-domain/invitation-codes-page.tsx`
- `docs/index.md`
- `tasks/index.md`
- `tasks/in-progress/` 与 `tasks/done/`

## 风险

- 树节点递归展示如果没有缩进和宽度约束，容易在窄屏溢出。
- 查询不存在用户时后端会返回错误，前端需要明确错误态，避免误认为无邀请关系。
- 排行榜为空时仍需要保留手动查询入口。
- 深度过大可能导致页面过长；前端需要提供有限深度选项。

## 验收标准

- Admin 邀请码页面展示“邀请树”管理区。
- 输入用户 ID 后可以调用 `/admin/invitation-codes/tree/{userId}` 并展示根节点与子节点。
- 点击排行榜用户的查看树按钮，可以填充根用户 ID 并触发查询。
- 无子节点时展示明确空态。
- 接口失败时展示 `InlineError`。
- `cd web; bun run typecheck` 通过。

## 测试边界

- 必跑 `cd web; bun run typecheck`。
- 如本地前端服务可用，使用浏览器验证页面非空、无明显 runtime overlay，并尝试邀请树查询交互。
- 不执行真实社交 OAuth、不执行真实注册链路、不校验真实生产数据。

## 当前状态

- 2026-05-25：需求创建，进入实现。
- 2026-05-25：Admin 邀请树前端接入、focused 测试、typecheck 和文档任务回写完成，需求归档为 Done。

## 实现结果

- `web/src/features/user-domain/invitation-codes-page.tsx` 新增 `InvitationTreeNode` 类型、根用户 ID、最大深度、已提交查询状态和树查询 `useQuery`。
- Admin 邀请码页面新增“邀请树”区，支持手动输入根用户 ID 查询。
- 邀请排行榜新增“邀请树”操作按钮，可从排行榜用户一键带入根用户并查询。
- 新增递归树形展示组件，展示用户身份、层级、邀请时间、直接下级数量和累计下级数量。
- 新增 `web/src/features/user-domain/invitation-codes-page.test.tsx`，覆盖手动查询和排行榜入口查询。

## 验证记录

- `cd web; bun run typecheck`
- `cd web; bun run test -- src/features/user-domain/invitation-codes-page.test.tsx`
- 浏览器尝试访问 `http://127.0.0.1:5173/console/invitation-codes`，前端可加载并正确重定向到登录，但当前后端登录 challenge API 不可用，页面级登录后实测未继续执行。

## 遗留边界

- 本轮未新增图谱布局库，邀请树使用递归列表展示。
- 本轮未把 `ownerUserId` 改成用户 picker。
- 浏览器端完整 Admin 登录后页面验证依赖后端 challenge API 可用后再补。
