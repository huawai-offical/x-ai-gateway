# TASK-20260501-023 真实社交 OAuth Provider Client 与 JWK 验签

状态：Done  
优先级：High  
来源：TASK-20260501-019 后续拆分  
关联任务：[TASK-20260501-019](../done/TASK-20260501-019-social-oauth-real-token-exchange.md)  
关联需求：[REQ-20260501-006](../../docs/requirements/REQ-20260501-006-fifth-priority-task-closure-design.md)

## 背景

当前已完成社交 OAuth token/profile exchange 的可插拔基础层和本地 mock 闭环，但尚未访问真实 provider endpoint。

## 目标

接入真实 Google/GitHub provider client，并为 QQ、WeChat、Meta、X 预留独立映射。

## 范围

- Google authorization code token exchange。
- Google id_token JWK 验签与 email_verified 校验。
- GitHub access_token exchange 与 user/email API 映射。
- provider clientId/clientSecret 本地配置与加密存储方案。
- provider 错误码与审计记录。

## 非目标

- 本轮不要求真实线上 Google/GitHub smoke。
- 本轮不接入 QQ、WeChat、Meta、X 的真实 endpoint。

## 详细设计

- `GatewayProperties.Oauth` 增加 Google/GitHub 社交 OAuth 配置。
- 新增 Google provider client：
  - POST token endpoint。
  - 校验 id_token JWS 签名、issuer、audience、exp、email_verified。
  - userinfo endpoint 作为 profile fallback。
- 新增 GitHub provider client：
  - POST access token endpoint。
  - GET user API。
  - GET emails API，选择 primary + verified 邮箱。
- `PortalSocialOAuthService` 按 client priority 选择真实 client，未配置时回退本地 client。

## 风险

- Provider 字段和错误码会变化，client 必须保留配置化 endpoint。
- JWK 缓存和 key rotation 需要后续持续优化。

## 验收标准

- Google/GitHub provider client 在配置凭证后优先于本地 mock client。
- Google id_token 可完成 RS256 + JWKS 验签，并校验 issuer、audience、exp、email_verified。
- GitHub 可通过 token、user、emails API 映射 external subject、displayName 和 verified email。
- 真实线上 smoke、更多 provider 与 JWK 缓存作为后续任务继续推进。

## 实现结果

- `GatewayProperties.Oauth` 已增加 Google/GitHub 社交 OAuth client 配置与 endpoint 配置。
- 新增 `GoogleSocialOAuthProfileClient`，支持 authorization code token exchange、id_token 验签和 userinfo fallback。
- 新增 `GitHubSocialOAuthProfileClient`，支持 authorization code token exchange、user API 与 emails API 映射。
- `PortalSocialOAuthService` 已按 priority 选择真实 client，未配置时保留本地 client 回退。

## 测试/验证情况

- 通过 `SocialOAuthProfileClientTests` 覆盖 Google JWKS 验签和 GitHub verified primary email 映射。
- 通过 `PortalSocialOAuthServiceTests` 覆盖 profile exchange 抽象、绑定、解绑和本地 fallback。
- 通过目标 Gradle 回归测试。

## 遗留问题

- 暂未接入 QQ、WeChat、Meta、X 的真实 endpoint。
- 暂未实现 JWKS 缓存与 key rotation 缓存策略。
- 暂未执行真实线上 Google/GitHub smoke。

## 后续建议

- 新增 `TASK-20260501-027` 推进更多社交 provider、JWK 缓存和线上 smoke checklist。
