# TASK-20260524-002-04 Portal 注册渠道策略与邀请码渠道

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-002](TASK-20260524-002-admin-oauth-removal-portal-social-oauth-config-parent.md)  
上游来源：[REQ-20260524-002](../../docs/requirements/REQ-20260524-002-admin-oauth-removal-portal-social-oauth-config.md)

## 背景

当前 Portal 注册策略已有邮箱域名、邀请码必填和邮箱验证后才能创建 Key 的控制项，但没有“允许哪些渠道注册用户”的统一开关。新增社交 OAuth 后，如果不把新用户创建纳入注册策略，关闭邮箱注册也无法阻止社交 OAuth 首次登录创建用户。

## 目标

- 注册策略增加允许注册渠道：`PASSWORD`、`SOCIAL_OAUTH`、`INVITE_CODE`。
- 邮箱密码注册按是否填写邀请码选择 `PASSWORD` 或 `INVITE_CODE` 渠道校验。
- 社交 OAuth 首次创建用户必须校验 `SOCIAL_OAUTH` 渠道。
- 当策略要求邀请码或使用 `INVITE_CODE` 渠道时，必须提供有效邀请码。
- Admin 注册策略 API 返回并保存渠道配置。

## 非目标

- 不新增邀请码批量生成、有效期、次数和审计报表。
- 不改变兑换码、订阅码和营销活动逻辑。
- 不改变已有邮箱域名限制和邮箱验证创建 Key 的语义。

## 输入

- `PortalSecurityService`
- `PortalRegistrationPolicyRequest`
- `PortalRegistrationPolicyResponse`
- `PortalAuthService`
- `PortalSocialOAuthService`
- Portal 注册页和 Admin 安全策略页。

## 输出

- 后端注册渠道策略。
- Portal 注册页按策略展示邮箱密码/邀请码注册入口。
- Admin 可配置注册渠道。
- 对应测试更新。

## 影响范围

- `/admin/security/registration-policy`
- `/portal/auth/register`
- `/portal/auth/oauth/{provider}/callback`
- Portal 注册页。

## 依赖

- 现有 `PortalSecurityService` 内存注册策略。
- 现有邀请码集合字段。

## 风险

- 默认渠道如果过窄，可能导致现有部署突然无法注册。
- 注册渠道和邀请码必填两个开关需要语义清晰，避免管理员误以为开放邀请码渠道等同于开放无邀请码注册。

## 验收标准

- 默认允许邮箱密码注册，保持兼容。
- 关闭 `PASSWORD` 后，无邀请码的邮箱密码注册失败。
- 开启 `INVITE_CODE` 并提供有效邀请码时可注册。
- 关闭 `SOCIAL_OAUTH` 后，社交 OAuth 不能创建新用户。
- Admin 注册策略响应包含 `allowedRegistrationChannels`。

## 测试边界

- `PortalSecurityService` 覆盖渠道允许、渠道关闭、邀请码必填和域名限制组合。
- `PortalAuthService` 覆盖邮箱密码与邀请码注册渠道选择。
- `PortalSocialOAuthService` 覆盖社交 OAuth 新用户创建渠道拦截。

## 关联文档

- [REQ-20260524-002](../../docs/requirements/REQ-20260524-002-admin-oauth-removal-portal-social-oauth-config.md)

## 当前状态

- 2026-05-24：已实现 `PASSWORD`、`INVITE_CODE`、`SOCIAL_OAUTH` 注册渠道策略；Portal 注册页提交邀请码；系统参数页可配置注册渠道策略。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.portal.application.PortalSecurityServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests"`
- `bun run typecheck`（工作目录：`web/`）
