# TASK-20260524-002-03 Portal 已注册用户社交 OAuth 绑定

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260524-002](TASK-20260524-002-admin-oauth-removal-portal-social-oauth-config-parent.md)  
上游来源：[REQ-20260524-002](../../docs/requirements/REQ-20260524-002-admin-oauth-removal-portal-social-oauth-config.md)

## 背景

Portal 已支持社交 OAuth 身份记录、列表和解绑，但 callback 当前更偏向登录/自动创建账号路径。用户如果最初通过邮箱密码注册，后续需要能够在登录态安全中心把社交 OAuth 身份绑定到自己的现有账号，而不是依赖 provider 邮箱匹配或创建新账号。

## 目标

- 已登录 Portal 用户可以从安全中心发起社交 OAuth 绑定。
- OAuth callback 检测到当前登录态时，将 provider 外部身份绑定到当前用户。
- 如果外部身份已绑定当前用户，更新 metadata 和最近登录时间。
- 如果外部身份已绑定其它用户，直接失败。
- 未登录 callback 仍保持社交登录语义，但不绕过注册渠道策略。

## 非目标

- 不实现真实第三方 OAuth 线上 smoke。
- 不改造社交身份表结构。
- 不允许一个外部身份跨用户复用。

## 输入

- `PortalSocialOAuthService`
- `PortalAuthController`
- `GatewayUserSocialIdentityRepository`
- `portal-security-page.tsx`
- `web/src/features/portal/api.ts`

## 输出

- 后端支持登录态社交身份绑定。
- Portal 安全中心展示可绑定 provider 并可发起绑定。
- 对应后端和前端测试更新。

## 影响范围

- Portal social OAuth start/callback。
- Portal 安全中心。
- 社交身份列表与解绑后的刷新。

## 依赖

- `TASK-20260524-002-02` 的 provider 可用性配置。
- 现有 Portal session。

## 风险

- 如果 callback 不校验已存在外部身份归属，可能发生账号接管。
- 如果绑定流程创建新用户，会造成同一用户身份分裂。

## 验收标准

- 登录态 callback 绑定到当前用户。
- 外部身份已属于其它用户时失败。
- 绑定成功后安全中心社交身份列表可看到新身份。
- 未配置或关闭的 provider 不能发起绑定。

## 测试边界

- `PortalSocialOAuthService` 覆盖登录态绑定、重复绑定、跨用户冲突。
- 前端安全中心覆盖 provider 绑定按钮与跳转 URL。

## 关联文档

- [REQ-20260524-002](../../docs/requirements/REQ-20260524-002-admin-oauth-removal-portal-social-oauth-config.md)

## 当前状态

- 2026-05-24：已实现登录态社交 OAuth callback 绑定当前用户、跨用户身份冲突失败和 Portal 安全中心绑定入口。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests"`
- `bun run typecheck`（工作目录：`web/`）
