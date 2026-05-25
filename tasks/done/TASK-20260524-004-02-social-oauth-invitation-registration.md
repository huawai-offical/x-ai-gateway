# TASK-20260524-004-02 社交 OAuth 首次注册邀请码支持

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-004](TASK-20260524-004-invitation-code-owner-oauth-rewards-parent.md)

## 背景

当前社交 OAuth 首次建号在注册策略要求邀请码时会直接失败，且 OAuth start/callback 没有保存邀请码上下文。该子任务负责让社交 OAuth 首次注册与邮箱注册一样支持邀请码。

## 目标

- `PortalSocialOAuthStartRequest` 增加 `inviteCode`。
- OAuth start 将邀请码写入 session metadata。
- OAuth callback 首次建号时读取邀请码并执行注册策略校验。
- OAuth 首次建号保存用户后核销邀请码并发放奖励。
- 已登录用户绑定社交身份不核销邀请码。

## 非目标

- 不做真实第三方 OAuth live smoke。
- 不改变已绑定社交身份登录逻辑。

## 上游来源

- [REQ-20260524-004](../../docs/requirements/REQ-20260524-004-invitation-code-owner-oauth-rewards.md)

## 输入

- `PortalSocialOAuthService`
- `PortalSecurityService`
- `InvitationCodeRedemptionService`
- `PortalSocialOauthSessionEntity.metadataJson`

## 输出

- OAuth start/callback 邀请码传递与核销。
- 社交 OAuth 注册策略测试。

## 影响范围

- `PortalSocialOAuthStartRequest`
- `PortalSocialOAuthService`
- `PortalSecurityService`
- `PortalSocialOAuthServiceTests`
- `PortalSecurityServiceTests`

## 依赖

- `portal_social_oauth_session` state 持久化。
- `InvitationCodeRedemptionService`

## 风险

- metadata JSON 拼接不当会破坏 scopes 或 inviteCode。
- callback 重试可能重复核销，需要依靠 usage 唯一约束和奖励引用保护。

## 验收标准

- `inviteCodeRequired=true` 时，OAuth 首次注册缺少邀请码硬失败。
- `inviteCodeRequired=true` 时，OAuth 首次注册携带邀请码可创建用户并核销。
- 已登录绑定 OAuth 不核销邀请码。
- 已存在绑定身份登录不再重复校验或核销邀请码。

## 测试边界

- `PortalSocialOAuthServiceTests`
- `PortalSecurityServiceTests`

## 关联文档

- [REQ-20260524-004](../../docs/requirements/REQ-20260524-004-invitation-code-owner-oauth-rewards.md)

## 关联任务

- [TASK-20260524-004](TASK-20260524-004-invitation-code-owner-oauth-rewards-parent.md)

## 当前状态

- 2026-05-24：待实现。
- 2026-05-24：已完成社交 OAuth start/callback 邀请码传递、首次建号核销和绑定不核销边界。

## 实现结果

- `PortalSocialOAuthStartRequest` 增加 `inviteCode`，保留三参构造器兼容测试和旧调用。
- `PortalSocialOAuthService.start` 将邀请码写入 session metadata。
- `PortalSocialOAuthService.complete` 在匿名 OAuth 首次创建用户时读取邀请码、执行注册策略校验，并调用 `InvitationCodeRedemptionService` 核销。
- 已登录用户绑定 OAuth 身份不核销邀请码。
- `PortalSecurityService` 对 `SOCIAL_OAUTH` 渠道支持邀请码必填校验。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests"`
