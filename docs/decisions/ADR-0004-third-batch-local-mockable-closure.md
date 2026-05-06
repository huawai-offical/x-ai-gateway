# ADR-0004 第三批任务采用本地 Mock 可验证闭环

状态：Accepted  
日期：2026-05-01  

## 背景

`TASK-003`、`TASK-004`、`TASK-009` 都是生产化大任务，完整形态依赖真实 WebSocket provider、支付渠道和账号安全外部系统。当前项目优先需要可测试的本地闭环，避免把外部依赖作为完成条件。

## 决策

本轮采用本地 mockable closure：

- Realtime 以 `mock_realtime` adapter、SSE replay 和 conformance summary 作为闭环。
- Payment 以 mock payment provider、订单状态机、webhook 幂等和余额账本入账作为闭环。
- Security 以系统配置、SSRF URL guard、敏感词 scanner、Provider Site 保存拦截和 Portal Social OAuth mock callback 作为闭环。

## 后果

- 能在无外部账号和无线上服务时完成测试。
- 不阻塞后续真实 provider / payment / OIDC 接入。
- 社交 OAuth 先完成 provider 元数据、授权 URL、state、用户绑定和 session，真实 token exchange / id_token 验签后续独立接入。
- 需要在任务遗留问题中明确真实外部集成仍需后续拆分。
