# ADR-0006 第五批任务采用“真实适配接口 + 本地可验证执行器”闭环

状态：Accepted  
日期：2026-05-01  
关联需求：[REQ-20260501-006 第五批最高优先级任务闭环设计](../requirements/REQ-20260501-006-fifth-priority-task-closure-design.md)

## 背景

剩余 High 任务中有多项涉及真实三方系统。为了继续保持本地优先协作流程，不能把交付结果绑定到线上 SaaS、真实商户号、真实 provider 凭证或外网 smoke。

## 决策

本轮采用以下策略：

- 对外部 provider：实现真实协议 client 和可配置 endpoint，但测试使用本地 mock HTTP exchange。
- 对分布式能力：先实现等价本地 store 和清晰接口，后续再替换 Redis。
- 对账号安全：先完成 TOTP、CAPTCHA、邮箱验证的后端闭环，Passkey/WebAuthn 留给后续浏览器交互任务。

## 影响

- 优点：可以在无外部账号、无真实回调域名、无 Redis 的本地环境完成可运行、可测试、可回归的闭环。
- 代价：真实邮件发送、WebAuthn ceremony、Redis 分布式状态、真实 provider 线上 smoke 仍需后续任务推进。
- 约束：凡是本地替代实现必须在命名和文档中明确其边界，不得把本地 mock 当成真实生产能力。

## 结果

已按本决策完成第五批任务闭环：

- Google/GitHub 真实 provider client 已实现，测试使用本地 mock HTTP exchange。
- Route Policy rate limit/circuit breaker 已实现本地等价 store，并保留后续 Redis 化边界。
- 账号安全已完成 TOTP、CAPTCHA、邮箱验证后端闭环，Passkey/WebAuthn 和真实邮件发送继续拆分到后续任务。
