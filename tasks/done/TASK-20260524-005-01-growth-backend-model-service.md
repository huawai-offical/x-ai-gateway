# TASK-20260524-005-01 增长奖励后端模型与核销服务

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-005](TASK-20260524-005-invitation-growth-system-parent.md)  
上游来源：[REQ-20260524-005](../../docs/requirements/REQ-20260524-005-invitation-growth-system.md)

## 背景

现有邀请码只能配置注册人 Token credits 奖励。新需求要求支持邀请人返佣、套餐赠品、权益组赠品、邀请层级和排行榜，因此需要先补齐持久化模型与核销事务。

## 目标

- 扩展邀请码奖励字段。
- 新增邀请关系与用户权益组赠品持久化。
- 核销服务发放注册人奖励、邀请人返佣、套餐和权益组赠品。
- 保证重复核销和 callback 重试幂等。

## 非目标

- 不实现现金返佣和支付分账。
- 不实现多活动策略引擎。

## 输入

- `InvitationCodeEntity`
- `InvitationCodeUsageEntity`
- `InvitationCodeRedemptionService`
- `GatewayUserBalanceLedgerEntity`
- `UserSubscriptionEntity`
- `AccessGroupEntity`

## 输出

- changelog、entity、repository 和核销服务改造。
- focused backend tests。

## 影响范围

- `src/main/resources/db/changelog/changes/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/`
- `src/main/java/com/prodigalgal/xaigateway/portal/application/InvitationCodeRedemptionService.java`
- `src/test/java/com/prodigalgal/xaigateway/portal/application/InvitationCodeRedemptionServiceTests.java`

## 依赖

- 用户、套餐、访问组和余额流水现有表。

## 风险

- 奖励重复发放。
- 邀请关系重复或自邀请。
- 权益组赠品未参与权益解析。

## 验收标准

- 核销成功后按邀请码配置发放全部奖励。
- 同一用户重复核销不重复发放。
- 邀请关系唯一且可追溯。
- 用户权益组赠品进入权益解析。

## 测试边界

- 后端单元测试覆盖奖励发放、幂等、关系和权益解析。

## 关联文档

- [REQ-20260524-005](../../docs/requirements/REQ-20260524-005-invitation-growth-system.md)

## 当前状态

- 2026-05-24：已完成后端模型、核销服务、权益解析接入和 focused tests，随父任务归档。

## 实现记录

- 新增 `db.changelog-0007-invitation-growth-system.yaml`，扩展邀请码奖励字段、邀请使用实际发放字段、`user_subscription.source_type/source_id`、`user_access_group_grant` 和 `invitation_relationship`。
- `InvitationCodeEntity` 支持邀请人返佣、赠送套餐、赠送权益组和赠品天数。
- `InvitationCodeUsageEntity` 记录邀请人、返佣、赠送套餐订阅和权益组授权实际结果。
- 新增 `InvitationRelationshipEntity` 与 `UserAccessGroupGrantEntity` 及 repository。
- `InvitationCodeRedemptionService` 在同一事务中完成注册人奖励、邀请人返佣、套餐赠品、权益组赠品和邀请关系落库，并使用稳定 `referenceType/sourceType + code:userId` 幂等。
- `AccessGroupEntitlementService` 已读取用户级权益组赠品，赠送权益组会进入分发 Key 权益解析。

## 测试/验证

- `.\gradlew.bat compileJava compileTestJava --no-daemon`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.InvitationGrowthServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.InvitationCodeRedemptionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.InvitationCodeAdminServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.auth.AccessGroupEntitlementServiceTests" --no-daemon`
