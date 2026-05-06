# TASK-20260501-000 x-ai-gateway 对标差距增强总览

状态：Done  
优先级：High  
来源：Linear X-281  
来源 URL：https://linear.app/x-ai/issue/X-281/x-ai-gateway对标-new-api-sub2api-cc-switch-的差距增强总览  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)
关联推进需求：[REQ-20260506-001](../../docs/requirements/REQ-20260506-001-seventh-priority-task-closure-design.md)

## 背景

基于本地三个参考项目进行横向分析：

- `D:/WorkSpace/Project/ai/参考/new-api-main`
- `D:/WorkSpace/Project/ai/参考/sub2api-main`
- `D:/WorkSpace/Project/ai/参考/cc-switch-main`

本轮目标是识别 `x-ai-gateway` 在功能完成度、功能完善度、功能丰富度、具体实现和产品细节上的差距，并形成可排期增强任务。

## 总体结论

`x-ai-gateway` 当前的网关治理内核、协议入口、能力矩阵、可观测和运维控制面已经较强；但相对参考项目，仍需要补齐供应商生态、SaaS 计费支付、Portal 用户自助、CLI/客户端生态、生产部署升级、安全登录体系、国际化与公开文档等产品化外圈。

## 范围

本父任务仅用于承接本轮差距增强总览与子任务索引，不直接进入实现。

## 拆分方向

- Provider Registry 与供应商预设
- Rerank / Video / Task 等非 Chat 资源族
- Realtime / Streaming 真实代理
- SaaS 支付、订单、账单与计费
- Portal 自助用量/账单/支付/渠道状态
- Codex / Claude / Gemini CLI 客户端生态接入
- 路由策略、熔断、限流与健康监控
- 安全体系、部署升级、监控 rollup、国际化与文档

## 子任务

- [TASK-20260501-001 Provider Registry 2.0](TASK-20260501-001-provider-registry-2.md) - Done
- [TASK-20260501-002 非 Chat 资源族扩展](TASK-20260501-002-non-chat-resources.md) - Done
- [TASK-20260501-003 Realtime 与 Streaming 真实代理闭环](TASK-20260501-003-realtime-streaming-proxy.md) - Done
- [TASK-20260501-004 SaaS 计费与支付闭环](TASK-20260501-004-billing-payment-loop.md) - Done
- [TASK-20260501-005 Portal 用户自助增强](../backlog/TASK-20260501-005-portal-self-service.md) - Backlog
- [TASK-20260501-006 编程类账号身份治理](../backlog/TASK-20260501-006-programming-account-identity.md) - Backlog
- [TASK-20260501-007 客户端接入包](../backlog/TASK-20260501-007-client-onboarding-pack.md) - Backlog
- [TASK-20260501-008 路由策略 2.0](TASK-20260501-008-routing-policy-2.md) - Done
- [TASK-20260501-009 安全体系增强](TASK-20260501-009-security-system.md) - Done
- [TASK-20260501-010 生产部署与升级体系](../backlog/TASK-20260501-010-production-deployment-upgrade.md) - Backlog
- [TASK-20260501-011 监控与账务 rollup](../backlog/TASK-20260501-011-monitoring-billing-rollup.md) - Backlog
- [TASK-20260501-012 国际化、公开文档与兼容性样例](../backlog/TASK-20260501-012-i18n-public-docs-compatibility.md) - Backlog

## 后续拆分任务状态

- 已完成：`TASK-20260501-013`、`014`、`015`、`018`、`019`、`020`、`022`、`023`、`025`、`027`、`028`。
- 仍在 backlog：`TASK-20260501-016`、`017`、`021`、`024`、`026`。
- 当前仍建议优先推进的 High backlog：真实 Video/Music Provider Executors、真实支付渠道与对账、真实 Realtime Provider WebSocket Adapter、Passkey/WebAuthn 注册策略与安全审计。

## 验收标准

- 子任务已按可独立排期粒度创建。
- 每个子任务关联本地报告。
- 后续进入实现前，在对应子任务和本地报告中补充实施方案、范围、风险与验收结果。

## 迁移记录

线上 Linear X-281 有 8 个已创建子任务和 4 个未创建子任务。未创建原因是 Linear 免费 issue 数量限制。详见 [MIG-20260501](../../docs/migrations/MIG-20260501-notion-linear-to-local.md)。

## 本批推进记录

- 2026-05-06：进入第七批高优先级任务闭环。父任务只承担总览和拆分，不重复承接子任务实现；本轮更新子任务状态后归档。

## 实现结果

- 父任务已完成“差距总览、拆分承接、状态索引”职责。
- 子任务链接按当前 `done`/`backlog` 目录更新。
- 剩余未完成事项保留在独立 backlog，不再由父任务重复承接。

## 测试/验证情况

- 已核对 `tasks/index.md` 中对标增强任务状态。
- 本任务为任务治理与索引闭环，无代码测试项。

## 遗留问题

- 父任务 Done 不代表所有子任务 Done；剩余 backlog 继续独立排期。

## 后续建议

- 下一批建议优先选择 `TASK-20260501-026`、`TASK-20260501-021`、`TASK-20260501-024` 或 `TASK-20260501-017` 中的三项继续推进。
