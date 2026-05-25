# REQ-20260524-005 邀请增长系统完整化

状态：Done  
日期：2026-05-24  
关联任务：[TASK-20260524-005](../../tasks/done/TASK-20260524-005-invitation-growth-system-parent.md)

## 背景

REQ-20260524-003 和 REQ-20260524-004 已完成邀请码库存、核销、归属人、OAuth 首次注册支持和 Token credits 注册奖励。用户进一步批准访问组/账号组分层设计，并要求把此前列为非目标的套餐、返佣、邀请层级和排行榜纳入本轮。

本轮需要把邀请码从“注册准入 + 一次性额度奖励”升级为完整增长系统：邀请码可以配置注册人奖励、邀请人返佣、赠送套餐和可选权益组；首次注册核销后形成可追溯邀请关系；系统能够展示邀请层级和排行榜。同时需要同步收口访问组/账号组语义，避免把权益授权和上游供给混为一体。

## 目标

- 邀请码支持配置注册人 Token credits 奖励、邀请人返佣 Token credits、赠送套餐和赠送权益组。
- 首次注册核销邀请码时，记录邀请关系，邀请人来源于邀请码归属人。
- 邀请关系支持层级查询，默认覆盖直接邀请、二级邀请和更深层级的统计展示。
- 邀请码核销奖励需要幂等，重复 callback 或重试不能重复发放额度、套餐、权益组或返佣。
- Admin 能查看邀请码层级明细和邀请排行榜。
- Portal 用户能查看自己的邀请统计、直接邀请用户和排行榜。
- 访问组在用户域中明确为权益/授权组；账号组在上游域中明确为上游账号组/凭证池。

## 非目标

- 不实现现金提现吗、真实支付分佣、税务结算或财务对账。
- 不实现多活动营销引擎、优惠券叠加、A/B 实验或排行榜反作弊。
- 不把账号组作为用户奖励或邀请码赠品。
- 不重构所有历史导航与文案；本轮只收口当前相关页面和文档口径。

## 输入

- [REQ-20260524-004](REQ-20260524-004-invitation-code-owner-oauth-rewards.md)
- `InvitationCodeEntity`、`InvitationCodeUsageEntity`
- `GatewayUserBalanceLedgerEntity`
- `SubscriptionPlanEntity`、`UserSubscriptionEntity`
- `AccessGroupEntity`、`AccessGroupEntitlementService`
- Admin 邀请码页面、访问组页面、账号组页面
- Portal 登录/注册、订阅与安全页面

## 输出

- 数据库迁移：邀请码扩展字段、邀请关系表、用户权益组赠品表或等价持久化结构。
- 后端 entity、repository、Admin/Portal DTO、service 和 controller。
- 注册核销时发放注册人奖励、邀请人返佣、套餐赠品和权益组赠品。
- Admin/Portal 邀请统计、层级和排行榜 API。
- 前端 Admin 邀请码配置、层级/排行榜展示；Portal 个人邀请统计展示。
- 访问组/账号组相关页面文案与文档口径收口。
- focused tests 与文档任务状态回写。

## 影响范围

- `src/main/resources/db/changelog/changes/`
- `InvitationCodeEntity`、`InvitationCodeUsageEntity`
- 新增邀请关系和用户权益组赠品相关 entity/repository
- `InvitationCodeRedemptionService`
- `InvitationCodeAdminService` 与 Admin API DTO/controller
- Portal 邀请统计 API
- `AccessGroupEntitlementService`
- `web/src/features/user-domain/invitation-codes-page.tsx`
- `web/src/features/user-domain/access-groups-page.tsx`
- `web/src/features/accounts/account-groups-page.tsx`
- Portal 相关邀请统计页面或入口

## 风险

- 邀请码核销、奖励发放和关系落库必须在同一事务内完成，否则可能出现已注册但未奖励或重复奖励。
- 层级邀请需要防止环路；首次注册时只能建立一次邀请关系。
- 返佣如果直接写余额流水，需要稳定引用键，否则 OAuth callback 重试会重复入账。
- 套餐赠品如果绕开 `UserSubscription`，访问组权益解析无法继承套餐授权。
- 权益组赠品如果误用账号组，会把用户权益和上游供给混在一起。
- 排行榜容易被误解为财务结算结果，本轮只表示邀请和 Token credits 返佣统计。

## 验收标准

- Admin 创建/编辑邀请码时可以配置注册人奖励、邀请人返佣、赠送套餐、套餐天数和赠送权益组。
- 邀请码响应和使用记录响应返回上述奖励配置与实际发放结果。
- 首次注册核销成功后生成邀请关系，邀请人与被邀请人不可相同，且同一被邀请人只能有一条邀请关系。
- 奖励发放幂等：同一邀请码同一用户重复核销不会重复写余额流水、订阅或权益组赠品。
- 赠送套餐后，用户拥有 active subscription，并可通过套餐绑定的访问组参与权益解析。
- 赠送权益组后，用户的 active access group 集合包含赠送权益组。
- 邀请人返佣写入 `gateway_user_balance_ledger`，引用类型与引用 ID 可追溯。
- Admin 和 Portal API 可以返回排行榜，至少包含邀请人数、直接邀请人数、返佣 Token credits 和最近邀请时间。
- Portal API 可以返回当前用户邀请统计和直接邀请列表。
- 访问组页面文案体现权益/授权组；账号组页面文案体现上游账号组/凭证池。

## 测试边界

- 后端 focused tests 覆盖邀请码奖励配置、核销幂等、套餐赠送、权益组赠送、返佣、层级关系和排行榜聚合。
- 前端至少执行 `bun run typecheck`。
- 不执行真实第三方 OAuth live smoke。
- 不执行真实支付或现金结算验证。

## 当前状态

- 2026-05-24：用户批准设计，并要求纳入套餐、返佣、邀请层级和排行榜；本需求创建并进入实施。
- 2026-05-24：本轮前端实现范围确认：Admin 邀请码页接入返佣、赠送套餐、赠送权益/授权组字段和排行榜；Portal 新增“我的邀请”页面接入 `/portal/invitations/summary`；访问组/账号组文案收口；验证口径为 `web` 目录 `bun run typecheck`。
- 2026-05-24：前端范围已完成：Admin 邀请码页、Portal “我的邀请”页面、导航和访问组/账号组核心文案已更新；`cd web; bun run typecheck` 通过。Admin 邀请树展示、`ownerUserId` picker 和非本轮点名页面历史文案保留为后续增强。
- 2026-05-24：后端模型、核销事务、Admin/Portal API、前端页面、命名收口和 focused 验证已完成，需求归档为 Done。

## 实现结果

- 新增 `db.changelog-0007-invitation-growth-system.yaml`，扩展邀请码奖励字段、邀请使用实际发放字段、`user_subscription.source_type/source_id`、`user_access_group_grant` 和 `invitation_relationship`。
- `InvitationCodeRedemptionService` 在注册核销事务中发放注册人奖励、邀请人返佣、赠送套餐、赠送权益组，并创建邀请关系；奖励和赠品均使用稳定 reference/source 键幂等。
- `AccessGroupEntitlementService` 读取用户级权益组赠品，赠送权益组会进入 active access group 与分发 Key 权益解析。
- `InvitationCodeAdminService`、Admin DTO 和 controller 支持返佣、套餐、权益组配置与使用记录实际发放结果。
- 新增 `InvitationGrowthService`，提供 Admin 排行榜、邀请树和 Portal 用户邀请统计。
- Portal 新增 `/portal/invitations/summary` 和前端“我的邀请”页面。
- Admin 邀请码页支持新奖励字段和排行榜。
- 访问组核心文案收口为“权益/授权组”，账号组核心文案收口为“上游账号组/凭证池”。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava --no-daemon`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.InvitationGrowthServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.InvitationCodeRedemptionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.InvitationCodeAdminServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.auth.AccessGroupEntitlementServiceTests" --no-daemon`
- `cd web; bun run typecheck`

## 遗留边界

- Admin 邀请树接口已提供，但前端本轮只展示排行榜，不展示树图。
- Admin 邀请码归属人仍使用 `ownerUserId` 数字输入，未实现用户 picker。
- 非本轮点名页面可能仍有少量历史“访问组/账号分组”文案，后续可在 UI 文案清理任务中继续收口。
