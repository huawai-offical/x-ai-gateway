# TASK-20260524-004-03 邀请码归属奖励与 OAuth 注册前端

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-004](TASK-20260524-004-invitation-code-owner-oauth-rewards-parent.md)

## 背景

Admin 邀请码页面当前只管理库存字段，Portal 注册页只在邮箱注册路径提交邀请码。该子任务负责让前端能够配置归属人/奖励，并在社交 OAuth 注册时传递邀请码。

## 目标

- Admin 邀请码页面展示和编辑 `ownerUserId`、归属邮箱/昵称、`rewardTokenCredits`。
- 批量创建邀请码支持设置归属人和奖励额度。
- Portal 注册视图点击社交 OAuth 时携带当前邀请码输入值。
- 已登录用户安全页绑定社交 OAuth 不传邀请码。

## 非目标

- 不实现归属用户搜索弹窗。
- 不重做 Portal 注册页视觉布局。

## 上游来源

- [REQ-20260524-004](../../docs/requirements/REQ-20260524-004-invitation-code-owner-oauth-rewards.md)

## 输入

- `web/src/features/user-domain/invitation-codes-page.tsx`
- `web/src/features/portal/portal-login-page.tsx`
- `web/src/features/portal/api.ts`
- `web/src/features/portal/types.ts`

## 输出

- Admin 邀请码页面字段更新。
- Portal OAuth start 参数更新。
- 前端 typecheck 通过。

## 影响范围

- Admin 用户域页面。
- Portal 登录/注册页面。
- Portal API client 与类型。

## 依赖

- 后端 InvitationCode DTO 新字段。
- 后端 `PortalSocialOAuthStartRequest.inviteCode`。

## 风险

- 如果只有后端支持而前端不传，真实用户在邀请码必填时仍无法完成 OAuth 首次注册。
- 如果绑定页误传邀请码，会产生非预期核销。

## 验收标准

- Admin 页面可以输入归属用户 ID 和奖励额度。
- 列表可看到归属和奖励信息。
- Portal 注册模式下社交 OAuth start request 包含邀请码。
- `bun run typecheck` 通过。

## 测试边界

- `bun run typecheck`。
- 不做浏览器端真实 OAuth 回跳。

## 关联文档

- [REQ-20260524-004](../../docs/requirements/REQ-20260524-004-invitation-code-owner-oauth-rewards.md)

## 关联任务

- [TASK-20260524-004](TASK-20260524-004-invitation-code-owner-oauth-rewards-parent.md)

## 当前状态

- 2026-05-24：待实现。
- 2026-05-24：已完成 Admin 邀请码归属/奖励字段和 Portal 注册页社交 OAuth 邀请码传递。

## 实现结果

- Admin 邀请码页面支持创建/编辑 `ownerUserId` 与 `rewardTokenCredits`。
- Admin 邀请码列表展示归属人和奖励额度，使用记录展示本次发放奖励。
- Portal 注册页加载社交 OAuth provider，注册模式发起 OAuth start 时携带邀请码。
- Portal 安全页绑定社交 OAuth 入口不传邀请码，避免绑定行为误核销。

## 验证记录

- `bun run typecheck`（工作目录：`web/`）
