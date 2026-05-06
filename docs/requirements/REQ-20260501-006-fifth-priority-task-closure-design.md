# REQ-20260501-006 第五批最高优先级任务闭环设计

状态：Done  
创建日期：2026-05-01  
关联 ADR：[ADR-0006 第五批任务采用“真实适配接口 + 本地可验证执行器”闭环](../decisions/ADR-0006-real-adapter-local-verification.md)  
关联任务：

- [TASK-20260501-022 Passkey/TOTP/验证码/邮箱验证](../../tasks/done/TASK-20260501-022-passkey-totp-captcha-email-verification.md)
- [TASK-20260501-023 真实社交 OAuth Provider Client 与 JWK 验签](../../tasks/done/TASK-20260501-023-social-oauth-real-provider-clients.md)
- [TASK-20260501-025 分布式 Route Policy 熔断/限流执行器](../../tasks/done/TASK-20260501-025-distributed-routing-circuit-rate-limit.md)

## 背景

前一批已完成社交 OAuth profile exchange 抽象、Route Policy runtime plan、Realtime WebSocket mock adapter。本轮继续选择剩余 High 任务中最能提升安全与运行时稳定性的三项：账号二次验证、真实 OAuth provider client、路由熔断/限流执行。

## 任务选择

本轮选择：

- `TASK-022`：账号安全是 Portal 和 SaaS 化的基础能力，且能完全本地闭环。
- `TASK-023`：承接社交 OAuth 登录需求，将 Google/GitHub 从本地 mock exchange 推进到真实 provider client 接口。
- `TASK-025`：承接 Route Policy runtime plan，将 rate limit/circuit breaker 推进到执行器与状态观测。

暂不选择：

- `TASK-017`：真实 Video/Music provider 执行器需要外部模型供应商凭证与长任务产物回调。
- `TASK-021`：真实支付渠道需要支付平台商户号、证书、回调域名和沙箱账号。
- `TASK-024`：真实 Realtime provider WebSocket 需要 provider 凭证、音频帧兼容和网络 smoke，本轮先把安全与路由执行器补齐。

## 目标

- 账号安全：新增验证码、邮箱验证、TOTP 设置/启用/禁用/登录校验基础能力。
- 社交 OAuth：新增 Google/GitHub provider client，支持 authorization code 换 token、Google id_token JWK 验签、GitHub user/email 映射。
- 路由策略：新增本地等价分布式 store 的 rate limit 与 circuit breaker 执行器，接入候选评估和请求成功/失败反馈。

## 范围

### TASK-022

- `GatewayUserEntity` 增加邮箱验证与 TOTP 状态字段。
- 新增 Portal 安全服务：
  - CAPTCHA challenge 生成/校验。
  - 邮箱验证 code 生成/确认。
  - TOTP secret 生成、setup URI、启用、禁用、登录校验。
- Portal 注册可校验 CAPTCHA；登录在用户启用 TOTP 后要求 TOTP code。
- 新增本地测试覆盖注册验证码、邮箱验证、TOTP 登录。

### TASK-023

- `GatewayProperties` 增加社交 OAuth provider client 配置。
- 新增 Google/GitHub `SocialOAuthProfileClient`：
  - Google：token endpoint、id_token 验签、userinfo fallback。
  - GitHub：token endpoint、user API、emails API。
- `PortalSocialOAuthService` 优先使用已配置真实 provider client，未配置时继续使用本地 client。
- 单元测试使用本地 mock HTTP exchange，不依赖真实互联网凭证。

### TASK-025

- 新增 `RoutingPolicyRuntimeEnforcementService`：
  - rate limit 计数。
  - circuit breaker open/half-open/closed 状态。
  - provider/site/credential/proxy 维度匹配。
- `GatewayRouteSelectionService` 在候选评估中调用执行器。
- `GatewayChatExecutionService` 在请求成功/失败后反馈执行器。
- Admin API 暴露运行时状态与 reset。

## 非目标

- 本轮不做 Passkey/WebAuthn 完整浏览器认证仪式。
- 本轮不发送真实邮件，只返回本地 verification code 供开发与测试闭环。
- 本轮不要求真实 Google/GitHub 线上 smoke，只实现可配置真实 client 并用 mock HTTP 覆盖协议路径。
- 本轮不引入 Redis，执行器先使用等价本地内存 store，后续可替换为 Redis。

## 风险

- TOTP secret 存储需要加密；本轮复用现有 AES/GCM 能力，避免明文落库。
- Google JWK 验签需要处理 kid/alg/aud/iss/exp/email_verified；实现必须对失败显式拒绝。
- 本地内存限流/熔断无法跨实例共享，生产部署前需要 Redis 化。

## 验收标准

- 用户可生成 CAPTCHA、完成邮箱验证、启用/禁用 TOTP，启用后登录必须携带正确 TOTP code。
- Google/GitHub provider client 可通过本地 mock HTTP 完成 profile 映射；Google id_token 签名、audience、issuer、过期、邮箱验证失败时拒绝。
- rate limit 超限时候选被过滤；circuit breaker 达阈值后打开，冷却后半开，成功后恢复；状态 API 可查询并 reset。

## 实现结果

- TASK-022 已完成后端安全闭环：CAPTCHA、邮箱验证码、TOTP setup/enable/disable、登录 TOTP 校验、用户安全字段与数据库迁移。
- TASK-023 已完成 Google/GitHub 真实 provider client：authorization code exchange、Google RS256 + JWKS id_token 验签、GitHub user/email 映射、priority 回退机制。
- TASK-025 已完成 Route Policy runtime 执行器：rate limit、circuit breaker、路由候选过滤、请求成功/失败反馈、Admin API 状态查询/reset、治理页状态面板。

## 验证情况

- Gradle 目标测试通过：`PortalAuthServiceTests`、`PortalSecurityServiceTests`、`PortalSocialOAuthServiceTests`、`SocialOAuthProfileClientTests`、`GatewayRouteSelectionServiceTests`、`RoutingPolicyRuntimeEnforcementServiceTests`、`GatewayChatExecutionServiceTests`、`GovernanceAdminServiceTests`。
- 前端测试通过：`bun run test -- src/features/ops/governance-page.test.tsx`。
- 前端类型检查通过：`bun run typecheck`。

## 遗留问题

- Passkey/WebAuthn、邀请/白名单注册策略、安全审计流水未在本轮实现。
- QQ、WeChat、Meta、X 真实社交 OAuth provider、JWKS 缓存与真实线上 smoke 未在本轮实现。
- Route Policy runtime 仍为本地内存 store，生产多实例需要 Redis 或等价共享状态后端。

## 后续建议

- 新增 `TASK-20260501-026`：Passkey/WebAuthn 浏览器 ceremony、注册策略与安全审计。
- 新增 `TASK-20260501-027`：更多社交 OAuth provider、JWK 缓存与线上 smoke checklist。
- 新增 `TASK-20260501-028`：Redis Route Policy Runtime Store 与跨实例一致性。
