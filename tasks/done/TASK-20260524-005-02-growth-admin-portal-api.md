# TASK-20260524-005-02 增长系统 Admin 与 Portal API

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-005](TASK-20260524-005-invitation-growth-system-parent.md)  
上游来源：[REQ-20260524-005](../../docs/requirements/REQ-20260524-005-invitation-growth-system.md)

## 背景

奖励模型落地后，需要把配置、层级和排行榜暴露给 Admin 与 Portal，支撑运营配置和用户自助查看。

## 目标

- Admin 邀请码 API 支持新的奖励字段。
- Admin API 提供邀请层级和排行榜。
- Portal API 提供当前用户邀请统计、直接邀请列表和排行榜。

## 非目标

- 不做现金结算 API。
- 不做公开匿名排行榜。

## 输入

- 后端奖励模型与邀请关系 repository。
- `InvitationCodeAdminController`
- `PortalAuthController`

## 输出

- DTO、service、controller 改造。
- focused tests。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/`
- `src/main/java/com/prodigalgal/xaigateway/portal/api/`
- `src/main/java/com/prodigalgal/xaigateway/portal/application/`

## 依赖

- TASK-20260524-005-01 的模型与聚合能力。

## 风险

- Portal 越权读取他人邀请明细。
- 排行榜口径与返佣流水口径不一致。

## 验收标准

- Admin 可查看指定用户或全局排行榜。
- Portal 只能查看自己的邀请统计和全局排行榜摘要。
- 响应字段能支撑前端展示。

## 测试边界

- 后端 service/controller focused tests。

## 关联文档

- [REQ-20260524-005](../../docs/requirements/REQ-20260524-005-invitation-growth-system.md)

## 当前状态

- 2026-05-24：Admin/Portal 后端 API 已接入并通过 focused tests，随父任务归档。

## 实现记录

- Admin 邀请码 request/response/usage response 已增加返佣、赠送套餐、赠送权益组和实际发放结果字段。
- 新增 `InvitationGrowthService`，统一提供排行榜、邀请树和 Portal 用户邀请统计。
- Admin 新增 `/admin/invitation-codes/leaderboard` 和 `/admin/invitation-codes/tree/{userId}`。
- Portal 新增 `/portal/invitations/summary`。

## 测试/验证

- `.\gradlew.bat compileJava compileTestJava --no-daemon`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.InvitationGrowthServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.InvitationCodeRedemptionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.InvitationCodeAdminServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.auth.AccessGroupEntitlementServiceTests" --no-daemon`
