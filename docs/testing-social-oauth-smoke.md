# 社交 OAuth 本地与线上 Smoke Checklist

状态：Active
关联任务：[TASK-20260501-027](../tasks/done/TASK-20260501-027-social-oauth-provider-expansion-jwk-cache-smoke.md)

## 目标

默认测试不依赖真实第三方账号；需要线上 smoke 时，通过环境变量提供临时凭证和回调域名，不把 `clientSecret`、access token 或真实用户资料写入仓库。

## 本地 Mock Contract

后端本地 contract 测试：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.portal.application.SocialOAuthProfileClientTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests"
```

覆盖范围：

- Google：token exchange、RS256 id_token 验签、JWKS 缓存、kid miss refresh 入口。
- GitHub：token、user、emails API，优先使用 verified primary email。
- QQ：token query-string、openid callback wrapper、userinfo 标准化。
- WeChat：openid/unionid、userinfo 标准化。
- Meta：Bearer userinfo、email/name 标准化。
- X：PKCE code verifier、Bearer `/users/me` profile 标准化。

## 线上 Smoke 前置条件

- 每个 provider 都使用独立开发者应用。
- 回调地址统一配置为：`{GATEWAY_PUBLIC_BASE_URL}/portal/auth/oauth/{provider}/callback`。
- 只使用测试账号，避免生产用户登录到本地环境。
- smoke 结束后撤销测试授权并清理本地用户绑定。

## 环境变量

```powershell
$env:GATEWAY_PUBLIC_BASE_URL="https://your-callback-domain.example"

$env:GATEWAY_GOOGLE_SOCIAL_CLIENT_ID="<google-client-id>"
$env:GATEWAY_GOOGLE_SOCIAL_CLIENT_SECRET="<google-client-secret>"

$env:GATEWAY_GITHUB_SOCIAL_CLIENT_ID="<github-client-id>"
$env:GATEWAY_GITHUB_SOCIAL_CLIENT_SECRET="<github-client-secret>"

$env:GATEWAY_QQ_SOCIAL_CLIENT_ID="<qq-client-id>"
$env:GATEWAY_QQ_SOCIAL_CLIENT_SECRET="<qq-client-secret>"

$env:GATEWAY_WECHAT_SOCIAL_CLIENT_ID="<wechat-client-id>"
$env:GATEWAY_WECHAT_SOCIAL_CLIENT_SECRET="<wechat-client-secret>"

$env:GATEWAY_META_SOCIAL_CLIENT_ID="<meta-client-id>"
$env:GATEWAY_META_SOCIAL_CLIENT_SECRET="<meta-client-secret>"

$env:GATEWAY_X_SOCIAL_CLIENT_ID="<x-client-id>"
$env:GATEWAY_X_SOCIAL_CLIENT_SECRET="<x-client-secret>"
```

## 手工 Smoke 步骤

1. 启动后端与前端，确认 `gateway.web.public-base-url` 与 provider 回调域名一致。
2. 调用 `GET /portal/auth/oauth/providers`，确认 provider 列表包含 `google`、`github`、`qq`、`wechat`、`meta`、`x`。
3. 对每个 provider 调用 start 接口，确认 authorization URL 使用配置的 `client_id`。
4. 完成 provider 授权回调后，确认本地用户、`GatewayUserSocialIdentityEntity`、session 状态为 `COMPLETED`。
5. 对 Google 轮换测试，可临时清理 JWKS cache 后重试，确认不会复用过期 key。
6. 测试解绑接口，确认只删除当前用户的对应社交身份。

## 验收记录

真实线上 smoke 不进入默认 CI。完成真实 smoke 后，在本地新增日期化报告，记录 provider、回调域名、测试账号标识脱敏值、结果和失败截图路径，不记录 secret 或 token。
