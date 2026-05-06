# TASK-20260501-027 社交 OAuth Provider 扩展、JWK 缓存与线上 Smoke

状态：Done
优先级：High
来源：TASK-20260501-023 后续拆分
关联任务：[TASK-20260501-023](../done/TASK-20260501-023-social-oauth-real-provider-clients.md)
关联需求：[REQ-20260501-006](../../docs/requirements/REQ-20260501-006-fifth-priority-task-closure-design.md)
关联推进需求：[REQ-20260505-002](../../docs/requirements/REQ-20260505-002-sixth-priority-task-closure-design.md)

## 背景

第五批已完成 Google/GitHub 真实 provider client 与 Google JWK 验签，但更多社交平台、JWK 缓存和真实线上 smoke 仍未闭环。

## 目标

将社交 OAuth 从 Google/GitHub 扩展到 QQ、WeChat、Meta、X，并补齐 provider key 缓存与线上验证清单。

## 范围

- QQ、WeChat、Meta、X provider client。
- JWK 缓存、kid miss refresh、key rotation 策略。
- provider 错误码标准化与审计记录。
- 真实凭证环境变量、回调域名、线上 smoke checklist。
- Provider capability 文档与本地 mock contract tests。

## 非目标

- 不在仓库中保存真实 client secret。
- 不依赖线上 SaaS 任务系统。

## 风险

- 国内外 provider 的 OAuth 字段、权限范围和审核要求差异较大。
- 线上 smoke 需要真实回调域名和开发者账号。

## 验收标准

- 每个 provider 都有独立 client、mock contract test 和配置文档。
- JWK 缓存可处理 key rotation。
- 真实 smoke checklist 可按环境变量执行。

## 本批推进记录

- 2026-05-05：进入第六批最高优先级任务闭环，目标是扩展 QQ、WeChat、Meta、X provider client，并补充 JWK/key 缓存与 smoke checklist。
- 2026-05-05：完成 QQ、WeChat、Meta、X 通用 OAuth2 profile client；补充 Google JWKS cache 与 kid miss refresh；Portal OAuth start 支持配置 clientId，X 增加 PKCE authorization 参数。
- 2026-05-05：补充 smoke checklist：[testing-social-oauth-smoke](../../docs/testing-social-oauth-smoke.md)。

## 实现结果

- 新增 `GenericOAuth2SocialOAuthProfileClient`，覆盖 QQ、WeChat、Meta、X 的 token exchange、userinfo 获取和 profile 标准化。
- 新增 `SocialOAuthJwksCache`，Google provider 复用缓存并在 kid miss 时刷新。
- `GatewayProperties.Oauth` 与 `application.yaml` 增加社交 OAuth provider 配置项和 JWK cache TTL。
- `PortalSocialOAuthService.start` 优先使用本地配置的 `clientId`，X provider 附带 PKCE `code_challenge`。

## 验证情况

- 已通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.portal.application.SocialOAuthProfileClientTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests"`。

## 遗留问题

- 未执行真实第三方线上 smoke，因为当前仓库不保存真实 provider secret；真实 smoke harness 已拆到 [TASK-20260505-006](../backlog/TASK-20260505-006-redis-oauth-ops-smoke-harness.md)。
