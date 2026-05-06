# REQ-20260501-004 第三批最高优先级任务闭环设计

状态：Done  
日期：2026-05-01  
关联任务：

- [TASK-20260501-003 Realtime 与 Streaming 真实代理闭环](../../tasks/done/TASK-20260501-003-realtime-streaming-proxy.md)
- [TASK-20260501-004 SaaS 计费与支付闭环](../../tasks/done/TASK-20260501-004-billing-payment-loop.md)
- [TASK-20260501-009 安全体系增强](../../tasks/done/TASK-20260501-009-security-system.md)

## 背景

前两轮已完成 Provider Registry、非 Chat 资源、路由说明、动态 catalog、media async task 和 route policy 配置。下一批优先补齐三条生产化主线：Realtime 可验证转发、SaaS 支付入账、安全策略拦截。

## 目标

- Realtime / Streaming 从模拟会话推进到可 conformance 检查的 mock/SSE 代理闭环。
- SaaS 计费从套餐/余额账本推进到可创建支付订单、接收 mock webhook、幂等入账。
- 安全体系从散落设置推进到可配置 SSRF 与敏感词拦截服务，并接入站点保存与请求安全检查。

## 本轮范围

### TASK-003 Realtime / Streaming

- 在 Live Session 中新增 conformance summary，检查连接、输入/输出事件、SSE replay、audio bytes、关闭状态。
- 增加 `mock_realtime` runtime adapter，提供无需真实上游的协议 smoke。
- Admin API 增加 conformance endpoint，便于本地 smoke 与 UI 展示。

### TASK-004 SaaS 计费与支付

- 新增 `payment_order`、`payment_audit_log` 持久模型。
- 增加 admin payment API：
  - 创建 mock 充值订单。
  - 查询订单。
  - 接收 mock webhook 幂等入账。
- webhook 成功后写入 `GatewayUserBalanceLedgerEntity`，余额以 token credits 计。

### TASK-009 安全体系增强

- 在系统设置中增加 `security` 配置：
  - SSRF 防护开关。
  - 是否允许私网地址。
  - 允许 host 列表。
  - 敏感词列表。
- 新增安全策略服务，支持 URL 安全校验与文本敏感词扫描。
- Provider Site 保存时校验 `baseUrlPattern`，阻断内网地址与未允许 host。
- 新增 Portal Social OAuth 登录骨架：
  - provider 覆盖 `google`、`qq`、`wechat`、`github`、`meta`、`x`。
  - 生成授权 URL 与 state。
  - mock callback 绑定或创建 `GatewayUserEntity`，并写入 Portal session。

## 非目标

- 不在本轮接真实 WebSocket 上游，不处理浏览器端二进制音频帧。
- 不接真实 Stripe / 国内支付渠道，不处理退款、税务、发票。
- 不实现完整 Passkey、TOTP 和验证码 UI。
- Social OAuth 本轮不对接真实 token exchange / id_token 验签，真实 provider secret 与签名校验后续拆分。

## 风险

- Realtime 本轮是 mock/SSE conformance，后续仍需真实上游 WebSocket adapter。
- Payment 本轮是 mock provider，可验证订单状态机和入账，真实渠道签名验签需后续接入。
- Security 本轮先覆盖 SSRF 与敏感词核心拦截，账号二次验证仍需拆分。

## 验收标准

- Realtime conformance 能识别连接、事件、SSE replay 和音频计量。
- Payment order 可创建、mock webhook 可幂等完成入账，重复 webhook 不重复加余额。
- Security policy 可保存配置，站点 URL 与文本扫描可命中并返回清晰原因。
- Social OAuth provider 可列出、可生成授权 URL，callback 可幂等绑定用户并登录 Portal session。
- 三项均有后端测试，任务完成后移动到 `tasks/done/`。

## 实现结果

- Realtime：新增 `mock_realtime` runtime adapter、Live Session conformance summary 和 `/admin/live-sessions/{sessionKey}/conformance`。
- Payment：新增 `payment_order`、`payment_audit_log`、mock payment order API、mock webhook 幂等入账和 Liquibase `0041`。
- Security：新增系统 `security` 配置、`SecurityPolicyService`、URL SSRF guard、敏感词扫描、Provider Site `baseUrlPattern` 保存校验。
- Social OAuth：新增 Portal social OAuth provider 列表、授权 URL 生成、state 会话、callback 绑定/创建用户并写入 Portal session，覆盖 `google`、`qq`、`wechat`、`github`、`meta`、`x`。

## 测试/验证

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.LiveSessionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.PaymentAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.SecurityPolicyServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthServiceTests" --tests "com.prodigalgal.xaigateway.portal.application.PortalAuthServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.SystemSettingsAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.AdminAuthControllerTests"`

## 遗留问题

- Realtime 仍需真实 WebSocket provider adapter、二进制音频帧与上游错误映射。
- Payment 仍需真实 Stripe / 国内支付渠道、签名验签、退款和对账。
- Social OAuth 当前完成 provider 元数据、state、授权 URL、mock callback 与用户绑定；真实 token exchange、id_token 验签、refresh、unlink 仍需后续任务。
- Passkey / TOTP / 验证码 / 邮箱验证尚未在本轮实现。
