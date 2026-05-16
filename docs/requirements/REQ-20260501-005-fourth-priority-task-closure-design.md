# REQ-20260501-005 第四批最高优先级任务闭环设计

状态：Done  
创建日期：2026-05-01  
关联 ADR：[ADR-0005 第四批任务采用“本地可验证适配层优先”闭环](../decisions/ADR-0005-local-verifiable-adapter-closure.md)  
关联任务：

- [TASK-20260501-018 路由策略 UI 与 Retry/Fallback 运行时执行](../../tasks/done/TASK-20260501-018-routing-policy-ui-runtime.md)
- [TASK-20260501-019 社交 OAuth 真实 Token Exchange 与账号解绑](../../tasks/done/TASK-20260501-019-social-oauth-real-token-exchange.md)
- [TASK-20260501-020 Realtime WebSocket Provider Adapter](../../tasks/done/TASK-20260501-020-realtime-websocket-provider-adapters.md)

## 背景

前三批任务已经完成 Provider Registry、非 Chat 资源、异步媒体、路由策略配置、Live Session mock conformance、支付订单骨架、安全设置和社交 OAuth 基础流程。本轮继续选择能直接补齐运行时闭环、又能在本地测试环境验证的三个高优任务。

## 任务选择

本轮选择：

- `TASK-018`：承接已完成的 route policy 配置与 summary API，让 retry/fallback/circuit/rate limit 配置可以被解析、预览并进入路由执行链路。
- `TASK-019`：承接用户追加的社交媒体 OAuth 登录需求，在现有 Google、QQ、WeChat、GitHub、Meta、X 元数据与 mock callback 基础上，补齐 token exchange 抽象、profile 映射、已绑定身份查询和解绑能力。
- `TASK-020`：承接 Live Session mock conformance，将 runtime adapter 扩展到 WebSocket transport 形态，补齐帧映射、transport 元数据和本地 conformance 验证。

暂不选择：

- `TASK-017` 与 `TASK-021`：真实媒体 provider、真实支付渠道更依赖外部凭证、签名密钥、沙箱账号和 provider 侧回调配置，本轮先补足可插拔适配层，降低后续接真实渠道的风险。
- `TASK-022`：Passkey/TOTP/验证码/邮箱验证优先级高，但当前用户刚追加社交 OAuth，且路由策略与 Realtime 已有未闭合后续任务，因此排在下一轮。

## 目标

- 路由策略：新增本地可测试的 runtime plan API，并让 chat 执行 fallback 最大尝试次数可由启用中的 retry policy 覆盖。
- 社交 OAuth：新增 provider token/profile client 抽象，支持 callback 中没有 `externalSubject` 时通过 token exchange 解析 profile；新增当前用户已绑定身份查询和解绑接口。
- Realtime：新增 WebSocket transport adapter 形态，提供 mock WebSocket provider adapter、本地 event frame 映射和 conformance transport 检查。

## 范围

### TASK-018

- 新增 route policy runtime plan 响应模型与服务。
- 解析启用中的 `retryPolicy`、`fallbackPolicy`、`circuitBreakerPolicy`、`rateLimitPolicy` JSON。
- 新增 admin API 返回 runtime plan 与 warnings。
- 将 `maxAttempts` 接入非流式与流式 chat fallback 计算。
- 管理端 route guard 表单补充四类策略 JSON 字段，并提交到后端。

### TASK-019

- 新增 `SocialOAuthProfileClient`、`SocialOAuthTokenExchangeRequest`、`SocialOAuthProfile`。
- 新增默认本地 `mock` profile client，保留真实 provider client 的扩展点。
- callback 支持通过 authorization code 兑换 profile。
- 新增已绑定身份列表响应和解绑请求。
- 对重复绑定、过期 state、解绑当前用户身份建立单元测试。

### TASK-020

- 给 `LiveSessionRuntimeAdapter` 增加 transport 标识。
- 新增 `mock_websocket_realtime` adapter，模拟 WebSocket connect/send/heartbeat/close 帧。
- Live Session metadata 和 conformance 响应暴露 transport。
- conformance 增加 WebSocket transport 检查。

## 非目标

- 不在本轮接入真实 Google/GitHub HTTP token endpoint、JWK 验签或真实 OpenAI/Gemini WebSocket 网络连接。
- 不在本轮引入 Redis 分布式 rate limit 或真实 circuit breaker 半开探测。
- 不在本轮处理支付真实渠道、媒体真实执行器、Passkey/TOTP。

## 风险

- `RouteGuardPolicy` 里的策略字段当前是自由 JSON 文本，解析需要容忍非法 JSON 并返回 warnings，避免阻塞既有规则。
- Gateway 执行链路构造参数多，接入 runtime policy service 必须保持测试构造器兼容。
- OAuth callback 既要保留 mock 测试入口，又要为真实 provider 留扩展点，避免把 mock 字段绑定成长期 API 负担。
- WebSocket adapter 本轮只做本地 mock transport，真实 provider 的认证、二进制帧和背压仍需后续任务承接。

## 验收标准

- `TASK-018`：admin runtime plan API 可返回 retry/fallback/circuit/rate limit 摘要；非法 JSON 有 warning；chat fallback 最大尝试次数可被 retry policy 限制；前端 route guard 表单可保存四类策略 JSON。
- `TASK-019`：callback 可在缺少 `externalSubject` 时通过 profile client 完成绑定登录；当前用户可查看和解绑自己的社交身份；已有 mock callback 路径不回退。
- `TASK-020`：`mock_websocket_realtime` 可完成 connect/send/heartbeat/close；metadata 和 conformance 响应包含 `websocket` transport；本地测试覆盖 event frame 与 conformance。

## 实现结果

- `TASK-018` 已完成 route policy runtime plan API、策略 JSON 解析、chat fallback 最大尝试次数接入，以及 Web Route Guard 表单四类策略 JSON 字段。
- `TASK-019` 已完成社交 OAuth token/profile exchange 抽象、本地 profile client、callback exchange fallback、当前用户 identity 列表和解绑接口。
- `TASK-020` 已完成 Live Session WebSocket transport 抽象、`mock_websocket_realtime` adapter、metadata transport 写入和 conformance WebSocket 检查。

## 验证情况

- 后端目标测试通过：
  - `GovernanceAdminServiceTests`
  - `GovernanceAdminControllerTests`
  - `GatewayChatExecutionServiceTests`
  - `LiveSessionServiceTests`
  - `PortalSocialOAuthServiceTests`
  - `PortalAuthServiceTests`
- 前端验证通过：
  - `bun run test -- src/features/ops/governance-page.test.tsx`
  - `bun run typecheck`
- `git diff --check` 对本轮触及文件通过，仅保留 Git 的 CRLF 提示。

## 遗留问题

- 真实 Google/GitHub/QQ/WeChat/Meta/X provider HTTP token exchange 与 JWK 验签仍需后续任务接入。
- 真实 OpenAI Realtime/Gemini Live WebSocket 网络 adapter 仍需后续任务接入。
- circuit breaker 与 rate limit 的分布式执行器、半开探测和运行时状态 UI 仍需后续任务接入。

## 后续建议

- 已推进并归档 [TASK-20260501-023 真实社交 OAuth Provider Client 与 JWK 验签](../../tasks/done/TASK-20260501-023-social-oauth-real-provider-clients.md)。
- 已推进并归档 [TASK-20260501-024 真实 Realtime Provider WebSocket Adapter](../../tasks/done/TASK-20260501-024-realtime-real-provider-websocket.md)。
- 已推进并归档 [TASK-20260501-025 分布式 Route Policy 熔断/限流执行器](../../tasks/done/TASK-20260501-025-distributed-routing-circuit-rate-limit.md)。
