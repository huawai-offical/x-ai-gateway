# TASK-20260501-009 安全体系增强：OIDC、2FA/Passkey/TOTP、验证码、SSRF、敏感词与注册策略

状态：Done  
优先级：High  
来源：Notion 待创建；Linear 创建失败  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)  
关联设计：[REQ-20260501-004](../../docs/requirements/REQ-20260501-004-third-priority-task-closure-design.md)

## 背景

对标 `new-api` 与 `Sub2API`，`x-ai-gateway` 需要补齐更完整的生产级账号安全、注册治理、请求安全和内容安全能力。

## 目标

形成可配置、可审计、可分级启用的安全体系。

## 范围

- OIDC / SSO 登录接入。
- 2FA、Passkey、TOTP 等二次验证能力。
- 注册验证码、邮箱验证、注册策略、邀请/白名单机制。
- SSRF 防护、上游地址校验、内网地址拦截。
- 敏感词、内容策略、请求拦截规则。
- 社交媒体 OAuth 登录：Google、QQ、WeChat、GitHub、Meta、X。
- 管理端安全配置与审计记录。

## 非目标

- 不一次性覆盖所有企业 IdP 的特殊方言。
- 不将安全策略写死在代码中。

## 验收标准

- 管理员可配置登录与注册安全策略。
- 普通用户关键操作可启用二次验证。
- 上游地址与代理请求具备 SSRF 防护。
- 敏感词/内容策略可配置、可命中、可审计。
- Portal 可列出社交 OAuth provider、生成授权 URL，并通过 callback 绑定或创建用户完成登录。

## 实现记录

- 系统设置新增 `security` 配置：SSRF 防护、私网允许开关、allowed hosts、sensitive words。
- 新增 `SecurityPolicyService` 与 `/admin/security/scan`，支持 URL guard 与敏感词扫描。
- `ProviderSiteAdminService` 保存站点时接入 URL 安全校验，阻断本机/私网地址和不在允许列表的 host。
- 新增 Portal Social OAuth 登录骨架：
  - `SocialOAuthProvider` 覆盖 `google`、`qq`、`wechat`、`github`、`meta`、`x`。
  - `/portal/auth/oauth/providers` 返回可用 provider。
  - `/portal/auth/oauth/{provider}/start` 生成授权 URL 和 state。
  - `/portal/auth/oauth/{provider}/callback` 通过 mock callback 绑定或创建用户，并写入 Portal session。
  - 新增 `portal_social_oauth_session` 与 `gateway_user_social_identity`，Liquibase `0042`。

## 测试/验证

- 通过：`SecurityPolicyServiceTests`
- 通过：`ProviderSiteAdminServiceTests`
- 通过：`PortalSocialOAuthServiceTests`
- 通过：`PortalAuthServiceTests`
- 通过：`SystemSettingsAdminControllerTests`、`AdminAuthControllerTests`

## 遗留问题

- 此任务此前因 Linear 免费 issue 数量限制未能创建线上 issue，现以本地任务为准。
- Social OAuth 尚未接真实 token exchange、id_token 验签、refresh token、账号解绑。
- Passkey / TOTP / 验证码 / 邮箱验证 / 邀请白名单仍需后续任务。
