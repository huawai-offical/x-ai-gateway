# ADR-0005 第四批任务采用“本地可验证适配层优先”闭环

状态：Accepted  
日期：2026-05-01  
关联需求：[REQ-20260501-005 第四批最高优先级任务闭环设计](../requirements/REQ-20260501-005-fourth-priority-task-closure-design.md)

## 背景

当前 backlog 中多个高优任务都涉及真实三方 provider、真实支付或真实社交登录。直接接入真实渠道会受凭证、回调域名、沙箱账号、区域限制和 provider 审核影响，容易让本地开发闭环被外部条件阻塞。

## 决策

第四批任务采用“本地可验证适配层优先”策略：

- 先把运行时策略、OAuth profile exchange、Realtime WebSocket transport 抽象做成可插拔接口。
- 每个抽象必须提供本地 mock/default 实现，并通过单元测试或组件测试验证。
- 真实 provider HTTP/WebSocket/签名验签作为后续任务继续接入，不阻塞本轮闭环。

## 影响

- 优点：本轮可以在无外部账号、无线上 SaaS、无 provider 凭证的情况下完成可运行、可测试、可回归的代码路径。
- 代价：真实 Google/GitHub token endpoint、真实 OpenAI/Gemini WebSocket、Redis 级别限流/熔断仍需要后续任务继续深化。
- 约束：所有 mock 能力必须明确命名为 mock/local/default，不得伪装成已完成真实 provider 集成。

## 备选方案

- 直接接真实 provider：短期交付不可控，且会把本地验收绑定到外部账号和网络条件。
- 只写设计不改代码：不能推进项目闭环，不符合本轮“设计后推进”的要求。

## 结果

采用本决策。
