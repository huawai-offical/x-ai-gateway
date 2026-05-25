# TASK-20260524-005 邀请增长系统完整化

状态：Done  
优先级：High  
类型：父任务  
上游来源：[REQ-20260524-005](../../docs/requirements/REQ-20260524-005-invitation-growth-system.md)

## 背景

邀请码已具备库存、核销、归属人和 Token credits 注册奖励能力。用户批准访问组/账号组分层设计后，要求把套餐、返佣、邀请层级和排行榜一起纳入本轮，形成可运营的邀请增长系统。

## 目标

- 扩展邀请码奖励配置，支持注册人奖励、邀请人返佣、套餐赠品和权益组赠品。
- 注册核销时落邀请关系，支持层级统计和排行榜。
- Admin/Portal 提供对应配置和查询入口。
- 同步收口访问组/账号组命名与边界。

## 非目标

- 不做现金返佣、提现、真实支付分账或财务结算。
- 不把账号组作为邀请码奖励。
- 不实现复杂营销活动引擎。

## 输入

- [REQ-20260524-005](../../docs/requirements/REQ-20260524-005-invitation-growth-system.md)
- 已完成的 [TASK-20260524-004](../done/TASK-20260524-004-invitation-code-owner-oauth-rewards-parent.md)
- 现有套餐、访问组、余额流水、Portal OAuth 注册链路。

## 输出

- 后端 schema/entity/repository/service/API 改造。
- Admin/Portal 前端配置与展示。
- focused tests、typecheck 和文档任务回写。

## 影响范围

- 数据库 changelog、邀请码 entity/repository、邀请关系 entity/repository、用户权益组赠品 entity/repository。
- `InvitationCodeRedemptionService`、`InvitationCodeAdminService`、Admin/Portal controller。
- `AccessGroupEntitlementService`。
- Admin 邀请码、访问组、账号组页面；Portal 邀请统计入口。

## 依赖

- `gateway_user`
- `gateway_user_balance_ledger`
- `subscription_plan` / `user_subscription`
- `access_group`
- Portal 注册策略与社交 OAuth 首次建号链路

## 风险

- 奖励幂等和事务一致性。
- 邀请层级环路和重复关系。
- 套餐和权益组赠品绕开现有权益解析。
- 访问组/账号组文案收口不彻底导致继续误用。

## 验收标准

- 子任务全部完成并通过对应验证。
- 邀请码奖励配置、核销、关系、层级、排行榜可闭环。
- 访问组/账号组语义在相关页面和文档中清晰区分。

## 测试边界

- 后端 focused tests 覆盖核心奖励和聚合。
- 前端 `bun run typecheck`。
- 不做真实 OAuth live smoke、真实支付结算或提现验证。

## 关联文档

- [REQ-20260524-005](../../docs/requirements/REQ-20260524-005-invitation-growth-system.md)
- [REQ-20260524-004](../../docs/requirements/REQ-20260524-004-invitation-code-owner-oauth-rewards.md)

## 关联任务

- [TASK-20260524-005-01](TASK-20260524-005-01-growth-backend-model-service.md)
- [TASK-20260524-005-02](TASK-20260524-005-02-growth-admin-portal-api.md)
- [TASK-20260524-005-03](TASK-20260524-005-03-growth-frontend-and-naming.md)

## 当前状态

- 2026-05-24：父任务创建，开始实施。
- 2026-05-24：前端与命名收口子任务 `TASK-20260524-005-03` 已完成并归档；父任务仍等待后端模型/API 子任务最终闭环。
- 2026-05-24：后端模型/API、前端、验证和文档回写完成，父任务归档。

## 实现记录

- `TASK-20260524-005-03`：Admin 邀请码页支持返佣、赠送套餐、赠送权益/授权组字段与排行榜；Portal 新增“我的邀请”页面；相关导航和页面文案收口。
- `TASK-20260524-005-01`：新增增长系统 schema、邀请关系、用户权益组赠品、核销奖励发放和权益解析接入。
- `TASK-20260524-005-02`：新增 Admin/Portal 增长系统 API、排行榜、邀请树和 Portal summary。

## 测试/验证

- `TASK-20260524-005-03`：`cd web; bun run typecheck` 通过。
- `.\gradlew.bat compileJava compileTestJava --no-daemon`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.InvitationGrowthServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.InvitationCodeRedemptionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.InvitationCodeAdminServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.auth.AccessGroupEntitlementServiceTests" --no-daemon`
- `cd web; bun run typecheck`

## 遗留问题

- Admin 邀请树接口暂不展示。
- `ownerUserId` 暂保留数字输入。
- 非本轮点名页面仍可能存在少量历史“访问组/账号分组”文案。
