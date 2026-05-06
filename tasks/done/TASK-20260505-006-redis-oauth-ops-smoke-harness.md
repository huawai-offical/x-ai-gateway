# TASK-20260505-006 Redis/OAuth/Ops Smoke Harness 硬化

状态：Done
优先级：High
来源：X-263 代码态审计、本轮 Redis/OAuth 实现
关联报告：[REP-20260505 X-263 代码态审计](../../docs/reports/REP-20260505-x263-code-state-audit.md)
关联需求：[REQ-20260505-002](../../docs/requirements/REQ-20260505-002-sixth-priority-task-closure-design.md)
关联推进需求：[REQ-20260505-003](../../docs/requirements/REQ-20260505-003-smoke-harness-hardening.md)

## 背景

本轮新增 Redis route policy runtime store 和社交 OAuth provider 扩展，但真实 Redis、真实 OAuth developer app 与 ops 演练不适合放入默认 CI，需要独立 smoke harness。

## 目标

提供可选、环境变量驱动的 smoke harness，串联 Redis runtime store、OAuth callback mock/真实开发者配置和 ops dry-run。

## 范围

- Redis runtime store 共享状态 smoke。
- Google/GitHub/QQ/WeChat/Meta/X OAuth 本地 mock callback contract。
- 可选真实 provider smoke checklist，不保存真实 secret。
- ops maintenance dry-run smoke。

## 非目标

- 不把真实第三方 OAuth 凭证写入仓库。
- 不让默认 `gradlew test` 依赖外部 Redis 或第三方账号。

## 风险

- 真实第三方 OAuth 审核和回调域名要求不一致，需要把 provider 差异写入 checklist。

## 验收标准

- 默认 CI 仍可无外部依赖通过。
- 设置环境变量后可以执行 smoke harness。
- smoke 输出能落到本地报告或测试日志，便于后续追踪。

## 本批推进记录

- 2026-05-05：进入第七批推进，先实现可选 smoke harness 和执行文档，默认测试保持无外部依赖。
- 2026-05-05：完成 Redis/OAuth/Ops 可选 smoke harness，新增执行文档与本地报告输出。

## 实现结果

- 新增 `SmokeHarnessSupport` 统一管理 smoke 开关和报告输出。
- 新增 Redis runtime store smoke：`XAG_SMOKE_REDIS=true` 时验证共享 Redis 状态、half-open lock 和定点 reset。
- 新增 OAuth mock smoke：`XAG_SMOKE_OAUTH_MOCK=true` 时验证 Google/GitHub/QQ/WeChat/Meta/X provider mock contract。
- 新增 Ops dry-run smoke：`XAG_SMOKE_OPS_DRY_RUN=true` 时验证 `PRECHECK`、`UPGRADE_CHECK`、`ROLLBACK_PLAN` dry-run 结果。
- 新增 [testing-smoke-harness](../../docs/testing-smoke-harness.md)，并更新 [testing-baseline](../../docs/testing-baseline.md)。

## 验证情况

- 默认外部依赖关闭时，smoke 测试编译通过并自动跳过外部依赖。
- 已实际执行 `XAG_SMOKE_OAUTH_MOCK=true` 与 `XAG_SMOKE_OPS_DRY_RUN=true` 的本地 smoke。
- 本地报告已写入 `build/reports/xag-smoke/`。
- 2026-05-05：Redis VM 启动后，已按 `spring.data.redis` 配置块默认连接信息执行真实 Redis smoke，通过 `RedisRuntimeStoreSmokeHarnessTests`。

## 遗留问题

- 真实第三方 OAuth 线上 smoke 仍需按 checklist 使用测试账号手工验证。
