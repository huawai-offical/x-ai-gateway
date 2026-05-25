# TASK-20260524-004-01 邀请码归属人与奖励后端模型

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-004](TASK-20260524-004-invitation-code-owner-oauth-rewards-parent.md)

## 背景

邀请码当前只记录库存、次数和核销记录，缺少归属人和奖励配置。该子任务负责把归属用户和奖励额度纳入持久化模型、Admin API 和核销服务。

## 目标

- `invitation_code` 增加 `owner_user_id` 与 `reward_token_credits`。
- `invitation_code_usage` 增加 `reward_token_credits`。
- Admin 创建/编辑/响应支持归属人和奖励额度。
- 核销成功时按邀请码配置写入用户余额流水，并在 usage 中记录本次奖励。

## 非目标

- 不实现奖励套餐、访问组赠送或返佣结算。
- 不实现归属用户搜索。

## 上游来源

- [REQ-20260524-004](../../docs/requirements/REQ-20260524-004-invitation-code-owner-oauth-rewards.md)

## 输入

- `InvitationCodeEntity`
- `InvitationCodeUsageEntity`
- `InvitationCodeAdminService`
- `InvitationCodeRedemptionService`
- `GatewayUserBalanceLedgerRepository`

## 输出

- 数据库迁移与 JPA 字段。
- Admin DTO/service 映射。
- 核销奖励发放逻辑与测试。

## 影响范围

- `src/main/resources/db/changelog/changes/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/InvitationCode*.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/InvitationCode*.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/InvitationCodeAdminService.java`
- `src/main/java/com/prodigalgal/xaigateway/portal/application/InvitationCodeRedemptionService.java`

## 依赖

- `GatewayUserRepository`
- `GatewayUserBalanceLedgerRepository`

## 风险

- 归属人 ID 未校验会产生不可追溯数据。
- 奖励流水不幂等会重复加额度。

## 验收标准

- 创建/编辑邀请码可设置归属人和奖励额度。
- 负数奖励被拒绝。
- 归属用户不存在被拒绝。
- 核销成功后奖励额度大于 `0` 时写入余额流水。
- 使用记录返回本次发放额度。

## 测试边界

- `InvitationCodeAdminServiceTests`
- `InvitationCodeRedemptionServiceTests`

## 关联文档

- [REQ-20260524-004](../../docs/requirements/REQ-20260524-004-invitation-code-owner-oauth-rewards.md)

## 关联任务

- [TASK-20260524-004](TASK-20260524-004-invitation-code-owner-oauth-rewards-parent.md)

## 当前状态

- 2026-05-24：待实现。
- 2026-05-24：已完成 schema/entity/Admin service/核销奖励发放，并通过 focused tests。

## 实现结果

- 新增 `db.changelog-0006-invitation-code-owner-rewards.yaml`。
- `InvitationCodeEntity` 增加 `ownerUser` 和 `rewardTokenCredits`。
- `InvitationCodeUsageEntity` 增加 `rewardTokenCredits`。
- Admin 创建/编辑支持归属用户 ID 和奖励额度，归属用户不存在或奖励为负数时硬失败。
- 核销成功后写入 `INVITATION_CODE` 余额流水，引用为 `code:userId`。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.InvitationCodeAdminServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.InvitationCodeRedemptionServiceTests"`
