# TASK-20260524-005-03 增长系统前端与访问组/账号组命名收口

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-005](TASK-20260524-005-invitation-growth-system-parent.md)  
上游来源：[REQ-20260524-005](../../docs/requirements/REQ-20260524-005-invitation-growth-system.md)

## 背景

Admin 已有邀请码页面，访问组和账号组也已有独立页面，但访问组/账号组命名容易造成重复感。增长系统需要在前端补齐奖励配置、层级和排行榜展示，同时把访问组明确为权益/授权组，把账号组明确为上游账号组/凭证池。

## 目标

- Admin 邀请码页面支持新奖励字段、层级和排行榜展示。
- Portal 支持查看个人邀请统计和排行榜。
- 访问组页面文案收口为权益/授权语义。
- 账号组页面文案收口为上游账号组/凭证池语义。

## 非目标

- 不重做全站信息架构。
- 不引入新的图表库。

## 输入

- Admin/Portal API。
- `web/src/features/user-domain/invitation-codes-page.tsx`
- `web/src/features/user-domain/access-groups-page.tsx`
- `web/src/features/accounts/account-groups-page.tsx`

## 输出

- 前端页面和 API client 改造。
- `bun run typecheck` 验证。

## 影响范围

- `web/src/features/user-domain/`
- `web/src/features/accounts/`
- `web/src/features/portal/`
- `web/src/app/navigation.ts`
- `web/src/app/router.tsx`
- `web/src/app/route-surfaces.ts`

## 依赖

- TASK-20260524-005-02 的 API 字段。

## 风险

- 表单字段过多导致 Admin 配置难用。
- 命名只改页面标题但未改描述，仍会误导使用。

## 验收标准

- Admin 能配置并查看全部奖励字段。
- Portal 能查看个人邀请统计。
- 访问组/账号组相关文案不再混用权益与上游供给语义。
- 前端 typecheck 通过。

## 测试边界

- `bun run typecheck`。
- 需要时补页面测试。

## 关联文档

- [REQ-20260524-005](../../docs/requirements/REQ-20260524-005-invitation-growth-system.md)

## 当前状态

- 2026-05-24：待实现。
- 2026-05-24：进入前端实现。边界固定为 Admin 邀请码奖励字段与排行榜、Portal 我的邀请页面、访问组/账号组命名收口；树接口先不展示；ownerUserId 暂保留数字输入；验证使用 `cd web; bun run typecheck`。
- 2026-05-24：已完成并准备归档到 `tasks/done/`。

## 实现结果

- 更新 `web/src/features/user-domain/invitation-codes-page.tsx`：补齐 `referrerRewardTokenCredits`、赠送套餐、赠送权益/授权组字段；创建/编辑 payload 拆分；列表和使用记录展示奖励与发放结果；接入 `/admin/plans?active=true`、`/admin/access-groups?active=true` 和 `/admin/invitation-codes/leaderboard?limit=20`。
- 新增 `web/src/features/portal/portal-invitations-page.tsx`，并更新 Portal `api.ts`、`types.ts`、`portal-shell.tsx`、`router.tsx`、`route-surfaces.ts`，接入 `/portal/invitations/summary` 与 `/portal/invitations` 页面入口。
- 更新 `web/src/app/navigation.ts`、`web/src/features/user-domain/access-groups-page.tsx`、`web/src/features/accounts/account-groups-page.tsx`、`web/src/features/accounts/account-group-detail-page.tsx`，将访问组主口径收口为“权益/授权组”，账号组主口径收口为“上游账号组/凭证池”。

## 测试/验证

- 2026-05-24：在 `web` 目录执行 `bun run typecheck`，通过。

## 遗留问题

- Admin 邀请树接口 `/admin/invitation-codes/tree/{userId}` 本轮按需求先不展示。
- `ownerUserId` 按需求暂保留数字输入，后续可单独改为用户 picker。
- Codex onboarding、公告受众等非本任务点名页面仍存在少量“账号分组/访问组”历史文案，未在本轮扩展修改。
