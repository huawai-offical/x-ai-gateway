# TASK-20260501-019 社交 OAuth 真实 Token Exchange 与账号解绑

状态：Done  
优先级：High  
来源：TASK-20260501-009 后续拆分  
关联任务：[TASK-20260501-009](TASK-20260501-009-security-system.md)  
关联需求：[REQ-20260501-005](../../docs/requirements/REQ-20260501-005-fourth-priority-task-closure-design.md)

## 背景

当前 Portal Social OAuth 已支持 Google、QQ、WeChat、GitHub、Meta、X 的 provider 元数据、授权 URL、state 和 mock callback 绑定登录。下一步需要接入真实 token exchange 与身份验签。

## 目标

完成真实社交 OAuth 登录安全闭环的可插拔基础层。

## 范围

- 各 provider 的 clientId/clientSecret 配置与加密存储。
- authorization code token exchange。
- id_token / userinfo 验签与 profile 映射。
- refresh / unlink / 重新绑定。
- provider callback 错误处理与审计。

## 非目标

- 本轮不直接访问真实 Google/GitHub/QQ/WeChat/Meta/X token endpoint。
- 本轮不实现 JWK 缓存和 id_token 加密学验签。

## 实现结果

- 新增 `SocialOAuthProfileClient`、`SocialOAuthTokenExchangeRequest`、`SocialOAuthProfile`，把 authorization code 到 profile 的交换流程抽象成 provider adapter。
- 新增 `LocalSocialOAuthProfileClient`，支持本地 mock token exchange；callback 缺少 `externalSubject` 时可通过 profile client 完成绑定登录。
- `PortalSocialOAuthService.complete` 支持 profile exchange、复用已绑定 identity、更新已有 identity 登录信息。
- 新增当前用户社交身份列表接口和解绑接口：
  - `GET /portal/auth/oauth/identities`
  - `DELETE /portal/auth/oauth/{provider}/identities`
- 新增 `GatewayUserSocialIdentityRepository` 当前用户维度查询与解绑查询方法。

## 测试/验证情况

- `PortalSocialOAuthServiceTests`
- `PortalAuthServiceTests`

## 遗留问题

- 真实 Google/GitHub token endpoint、userinfo、id_token JWK 验签尚未接入。
- QQ、WeChat、Meta、X 的 provider 字段映射、错误码和审核流程差异需要独立适配。

## 后续建议

- 继续推进 [TASK-20260501-023 真实社交 OAuth Provider Client 与 JWK 验签](../backlog/TASK-20260501-023-social-oauth-real-provider-clients.md)。
