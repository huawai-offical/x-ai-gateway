# REQ-20260524-003 Portal 完整邀请码系统

状态：Done  
日期：2026-05-24  
关联任务：[TASK-20260524-003](../../tasks/done/TASK-20260524-003-portal-invitation-code-system-parent.md)

## 背景

当前 Portal 注册策略已支持 `INVITE_CODE` 渠道，但邀请码本身仍保存在注册策略的内存白名单中，只能判断“字符串是否存在”。这种实现不能支撑库存管理、批量生成、启停、过期、次数限制、核销记录和后续审计，也无法在服务重启后保持一致。

用户要求接入完整的邀请码系统。该系统应成为 Portal 注册入口的真实准入资源，而不是继续作为系统参数里的临时字符串集合。

## 目标

- 新增持久化邀请码资源，支持 code 唯一、启停、最大使用次数、已用次数、过期时间、备注和创建/更新时间。
- 新增邀请码核销记录，记录邀请码、注册用户、注册邮箱、使用渠道、使用时间和注册请求来源。
- Admin 提供邀请码列表、批量导入/生成、编辑、停用/删除和使用记录查询。
- Portal 邮箱密码注册在使用 `INVITE_CODE` 渠道或策略要求邀请码时，必须通过邀请码系统校验并核销。
- 社交 OAuth 首次创建用户若注册策略要求邀请码，必须硬失败；本轮不通过 OAuth callback 携带邀请码。
- 注册策略继续保留渠道控制和 `inviteCodeRequired`，但不再把 `inviteCodes` 作为真实库存来源。
- 邀请码校验必须是硬失败，不返回伪成功或模糊可用状态。

## 非目标

- 不把兑换码、营销活动码和注册邀请码合并为同一个资源。
- 不给邀请码增加奖励余额、套餐绑定或营销活动能力。
- 不在本轮实现邀请码转赠、审核流、CSV 导出和复杂报表。
- 不在本轮实现社交 OAuth 注册前的独立邀请码暂存流程。
- 不重做 Portal 注册页整体视觉设计。

## 输入

- `PortalSecurityService` 中现有注册渠道策略。
- `PortalAuthService.register` 邮箱密码注册路径。
- `PortalSocialOAuthService` 社交 OAuth 首次创建用户路径。
- `SecurityAdminController` 和用户域 Admin 页面模式。
- 现有 `RedeemCode`/`RedeemCodeUsage` 的批量码管理实现模式。

## 输出

- `invitation_code` 与 `invitation_code_usage` 数据表和 JPA entity/repository。
- Admin 邀请码 API：列表、创建/批量生成、编辑、删除、使用记录查询。
- 邀请码应用服务，提供校验、核销和响应 DTO 映射。
- Portal 注册接入邀请码核销。
- Admin 前端邀请码管理页和导航入口。
- 单元测试和前端 typecheck 验证。

## 影响范围

- 数据库 changelog。
- `infra.persistence.entity` 与 `infra.persistence.repository`。
- `admin.api` 与 `admin.application`。
- `portal.application.PortalAuthService`、`PortalSecurityService`、`PortalSocialOAuthService`。
- `web/src/features/user-domain`、`web/src/app/navigation.ts`、`web/src/app/router.tsx`。
- 现有注册策略文档与任务索引。

## 风险

- 如果校验和用户创建不在同一事务里，可能出现用户创建成功但邀请码未核销，或邀请码被重复使用。
- 如果只按 `used_count` 读写但没有唯一使用记录或事务保护，并发注册可能超用。
- 如果继续保留策略白名单作为库存来源，会让管理员误以为配置里的旧邀请码仍然可用。
- 如果删除已使用邀请码，会破坏核销记录可追溯性。

## 验收标准

- 邀请码持久化保存，服务重启后不丢失。
- Admin 可以批量粘贴或自动生成邀请码，并配置启用状态、最大使用次数和过期时间。
- Admin 可以编辑未删除邀请码的启用状态、最大使用次数和过期时间；最大使用次数不得小于已用次数。
- 已核销邀请码不能删除，只能停用；未核销邀请码可以删除。
- 注册时提供不存在、停用、过期或次数用尽的邀请码必须失败。
- 注册成功后必须写入使用记录并增加 `usedCount`。
- 同一用户不能重复核销同一个邀请码。
- 注册策略公开响应只暴露是否已有邀请码库存，不暴露邀请码明文列表。
- 社交 OAuth 首次建号在 `inviteCodeRequired=true` 时失败并提示该路径不支持邀请码注册。

## 测试边界

- 后端：邀请码 Admin service 覆盖创建、重复、编辑、删除、使用记录。
- 后端：Portal 注册覆盖邀请码成功核销、无效邀请码失败、重复核销拦截。
- 后端：注册策略响应的 `inviteCodesConfigured` 来自邀请码库存。
- 前端：Admin 邀请码页面能通过 typecheck，主要交互使用现有 React Query 模式。
- 不执行真实社交 OAuth 线上 smoke。

## 当前状态

- 2026-05-24：根据用户要求创建需求，准备拆分任务并实施。
- 2026-05-24：已完成持久化邀请码库存、核销记录、Admin API、Admin 页面、Portal 注册核销和注册策略语义收口。

## 实现结果

- 新增 `invitation_code` 与 `invitation_code_usage` 表，挂入 `db.changelog-0005-invitation-codes.yaml`。
- 新增 `InvitationCodeEntity`、`InvitationCodeUsageEntity`、repository、Admin request/response DTO、`InvitationCodeAdminService` 和 `/admin/invitation-codes` controller。
- Admin 支持邀请码列表、keyword/active 筛选、批量粘贴/自动生成、编辑启停/次数/过期/备注、删除未使用邀请码、查看使用记录。
- `InvitationCodeRedemptionService` 负责注册核销，使用 pessimistic write lock 校验并递增 `usedCount`，成功/失败写入审计日志。
- `PortalAuthService.register` 在用户创建后同事务核销邀请码；邀请码不存在、停用、过期、次数用尽或重复使用时失败。
- `PortalSecurityService` 不再把 `inviteCodes` 作为真实库存来源；注册策略只管渠道、邮箱域名、是否要求邀请码和邮箱验证要求。
- `inviteCodesConfigured` 改为来自持久化邀请码库存，并且只统计启用、未过期、未用尽的邀请码。
- 系统参数页移除手填邀请码列表，只展示库存状态并引导管理员到用户域“邀请码”页面管理。
- Admin 导航新增 `/console/invitation-codes`，使用独立邀请码管理页，避免与兑换码活动混淆。

## 验证记录

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.InvitationCodeAdminServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.InvitationCodeRedemptionServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests"`
- `bun run typecheck`（工作目录：`web/`）

## 遗留边界

- 本轮未实现社交 OAuth 首次注册前的邀请码暂存流程；当 `inviteCodeRequired=true` 时，社交 OAuth 首次建号会硬失败。
- 本轮未实现 CSV 导出、复杂报表、邀请码归属人或营销活动绑定。
- 当前 Admin 列表仍使用前端分页；如果邀请码量很大，后续可升级为服务端分页。
- 2026-05-24：社交 OAuth 首次注册邀请码暂存、邀请码归属人和 Token credits 奖励已由 [REQ-20260524-004](REQ-20260524-004-invitation-code-owner-oauth-rewards.md) 承接并闭环；本段保留为 REQ-003 原始交付边界记录。
