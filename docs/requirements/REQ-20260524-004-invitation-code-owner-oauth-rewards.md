# REQ-20260524-004 邀请码归属、OAuth 首次注册与奖励赠品

状态：Done  
日期：2026-05-24  
关联任务：[TASK-20260524-004](../../tasks/done/TASK-20260524-004-invitation-code-owner-oauth-rewards-parent.md)

## 背景

REQ-20260524-003 已将邀请码从注册策略白名单升级为持久化库存和核销记录，但仍把社交 OAuth 首次建号、邀请码归属人和奖励赠品列为遗留边界。现在产品口径进一步收紧：邀请码不是某个注册表单的附属字段，而是所有首次注册渠道共享的准入与权益载体。

用户要求邀请码需要记录归属人；社交 OAuth 首次注册也必须支持邀请码；不管用户通过邮箱密码、邀请码渠道还是社交 OAuth 首次建号，都需要能够携带并核销邀请码。邀请码还需要能够配置额度等赠品，注册核销成功后按邀请码配置发放。

## 目标

- 邀请码增加归属人字段，支持 Admin 创建和编辑时指定归属用户。
- 邀请码增加奖励额度配置，核销成功后给注册用户写入余额流水。
- 邀请码使用记录记录本次实际发放的奖励额度，便于审计。
- Portal 邮箱密码注册继续支持邀请码核销和奖励发放。
- Portal 社交 OAuth 首次创建用户支持在 start 阶段携带邀请码，并在 callback 建号后核销。
- 注册策略 `inviteCodeRequired=true` 时，社交 OAuth 首次建号不再因渠道本身失败，而是要求存在邀请码并在建号后真实核销。
- 已登录用户绑定社交 OAuth 不消耗邀请码，也不触发首次注册奖励。
- Admin 前端邀请码页面支持配置归属用户和奖励额度。
- Portal 前端注册视图发起社交 OAuth 时携带用户填写的邀请码。

## 非目标

- 不在本轮实现复杂营销活动、套餐绑定、访问组赠送或多种赠品类型。
- 不在本轮实现邀请码归属人的收益结算、返佣、层级邀请关系或邀请排行榜。
- 不在本轮实现用户搜索选择器；归属人先以用户 ID 输入为主。
- 不执行真实第三方 OAuth 线上 smoke；使用 service tests 覆盖 state 暂存和 callback 核销。

## 输入

- [REQ-20260524-003](REQ-20260524-003-portal-invitation-code-system.md) 中已落地的邀请码库存和核销服务。
- `PortalSecurityService` 注册渠道策略。
- `PortalSocialOAuthService.start/complete` state 会话模型。
- `GatewayUserBalanceLedgerEntity` 用户余额流水模型。
- Admin 邀请码页面和 Portal 注册页现有交互。

## 输出

- 数据库迁移：邀请码归属人、奖励额度、使用记录奖励额度。
- 后端 entity、repository、Admin DTO/service、Portal 核销服务和 OAuth 建号路径改造。
- 前端 Admin 邀请码页面字段与 Portal 社交 OAuth start 参数。
- focused 后端测试和前端 typecheck。
- 文档和任务状态回写。

## 影响范围

- `src/main/resources/db/changelog/changes/`
- `InvitationCodeEntity`、`InvitationCodeUsageEntity`
- `InvitationCodeAdminService` 与相关 DTO
- `InvitationCodeRedemptionService`
- `PortalSecurityService`
- `PortalSocialOAuthService` 与 OAuth request DTO
- `web/src/features/user-domain/invitation-codes-page.tsx`
- `web/src/features/portal/portal-login-page.tsx`
- `web/src/features/portal/api.ts` 与 types

## 风险

- 如果 OAuth start 未保存邀请码，真实第三方 callback 回来后将无法完成要求邀请码的首次建号。
- 如果邀请码奖励与核销不在同一事务内，可能出现已核销但未发放，或重复发放。
- 如果奖励流水引用不唯一，重复 callback 或重试可能导致额度重复增加。
- 如果绑定社交账号误用邀请码，会把非首次注册行为误当成注册奖励。
- 如果归属人字段只接受任意数字而不校验用户存在，会留下不可追溯的邀请码来源。

## 验收标准

- Admin 创建/编辑邀请码时可以设置 `ownerUserId` 和 `rewardTokenCredits`。
- 邀请码响应和使用记录响应返回归属人、奖励额度和使用时发放额度。
- 归属用户不存在时创建/编辑失败。
- 奖励额度为负数时创建/编辑失败；未设置时默认为 `0`。
- 注册核销成功且奖励额度大于 `0` 时写入 `gateway_user_balance_ledger`。
- 奖励流水使用稳定引用，避免同一邀请码同一用户重复发放。
- 社交 OAuth start 支持 `inviteCode`，并在 session metadata 暂存。
- 社交 OAuth 首次创建用户时读取 session 中的邀请码，执行注册策略校验和邀请码核销。
- 已登录用户绑定社交 OAuth 不读取、不核销邀请码。
- `inviteCodeRequired=true` 且 OAuth 首次注册未携带邀请码时硬失败。

## 测试边界

- 后端：Admin 邀请码服务覆盖归属人与奖励字段。
- 后端：邀请码核销服务覆盖奖励流水写入与使用记录奖励额度。
- 后端：Portal OAuth service 覆盖携带邀请码首次建号、缺失邀请码硬失败、绑定路径不核销。
- 后端：Portal security service 覆盖社交 OAuth 渠道要求邀请码。
- 前端：`bun run typecheck`。
- 不执行真实第三方 OAuth live smoke。

## 当前状态

- 2026-05-24：根据用户新增要求创建需求，准备实施。
- 2026-05-24：已完成邀请码归属人、奖励额度、社交 OAuth 首次注册邀请码核销、Admin/Portal 前端接入和 focused 验证。

## 实现结果

- 新增 `db.changelog-0006-invitation-code-owner-rewards.yaml`，为 `invitation_code` 增加 `owner_user_id`、`reward_token_credits`，为 `invitation_code_usage` 增加 `reward_token_credits`。
- `InvitationCodeEntity` 关联 `GatewayUserEntity ownerUser`，并记录邀请码级奖励额度。
- `InvitationCodeUsageEntity` 记录本次核销实际发放的奖励额度。
- `InvitationCodeAdminService` 支持创建/编辑时设置归属用户 ID 和奖励额度，并校验归属用户存在、奖励额度非负。
- `InvitationCodeResponse` 返回归属用户 ID、邮箱、显示名称和奖励额度；`InvitationCodeUsageResponse` 返回本次奖励额度。
- `InvitationCodeRedemptionService` 在核销成功后按邀请码配置写入 `gateway_user_balance_ledger`，引用为 `INVITATION_CODE` + `code:userId`，避免同一邀请码同一用户重复发放。
- `PortalSecurityService` 不再硬拒绝社交 OAuth 邀请码注册；当 `inviteCodeRequired=true` 时，`SOCIAL_OAUTH` 渠道同样要求邀请码非空。
- `PortalSocialOAuthStartRequest` 增加 `inviteCode`，`PortalSocialOAuthService.start` 将邀请码写入 OAuth session metadata，callback 首次创建用户后核销邀请码；已登录用户绑定社交 OAuth 不核销邀请码。
- Portal 注册页增加社交 OAuth provider 按钮，注册模式下发起 OAuth start 时携带当前邀请码；安全页绑定入口继续不传邀请码。
- Admin 邀请码页面支持输入归属用户 ID、奖励 Token 额度，并在列表和使用记录中展示相关信息。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.InvitationCodeAdminServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.InvitationCodeRedemptionServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests"`
- `bun run typecheck`（工作目录：`web/`）

## 遗留边界

- 本轮奖励类型先落地为 Token credits；套餐、访问组、返佣、层级邀请和排行榜仍不在本轮范围。
- Admin 归属人选择先使用用户 ID 输入，未做用户搜索选择器。
- 未执行真实第三方 OAuth live smoke；已用 service tests 覆盖 state 暂存、callback 核销和绑定不核销边界。
