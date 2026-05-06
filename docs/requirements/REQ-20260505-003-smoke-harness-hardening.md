# REQ-20260505-003 Redis/OAuth/Ops Smoke Harness 硬化

状态：Done
创建日期：2026-05-05
关联任务：[TASK-20260505-006](../../tasks/done/TASK-20260505-006-redis-oauth-ops-smoke-harness.md)
关联报告：[REP-20260505 X-263 代码态审计](../reports/REP-20260505-x263-code-state-audit.md)

## 背景

Redis route policy runtime store、社交 OAuth provider 扩展和 Ops/Maintenance 运行态都已经具备本地可测骨架，但真实 Redis、真实第三方 OAuth 凭证和 ops dry-run 串联不应进入默认 CI。本需求用于补齐可选 smoke harness，让需要时可以通过环境变量打开更严格验证，同时保持默认测试无外部依赖。

## 目标

- 提供可选、环境变量驱动的 smoke harness。
- 默认 `gradlew test` 不依赖外部 Redis、第三方 OAuth 或真实部署系统。
- Smoke 结果可在测试报告和本地文档中追踪。
- 明确真实 secret、access token、测试账号资料不得写入仓库。

## 范围

- Redis runtime store 共享状态 smoke：验证共享 Redis 下 rate window、circuit state、half-open lock 和 reset。
- OAuth mock contract smoke：串联 Google/GitHub/QQ/WeChat/Meta/X 的 provider client 与 portal start 参数。
- Ops maintenance dry-run smoke：验证维护运行 dry-run 能生成状态、detail、审计入口的可重放证据。
- 文档化 smoke 环境变量、执行命令和结果记录方式。

## 非目标

- 不在默认 CI 中连接真实 Redis 或真实第三方 OAuth。
- 不把真实 OAuth secret、Redis 密码、access token、用户资料写入仓库。
- 不接入生产升级系统或真实备份目标。

## 风险

- 可选 smoke 容易被默认测试漏跑，需要文档和命令清晰。
- 真实第三方 provider 的回调域名、审核状态、scope 差异较大，线上 smoke 仍需人工确认。
- Ops dry-run 与真实执行仍有差异，报告中必须保留 dry-run 标记。

## 验收标准

- 新增 smoke harness 在默认环境下可被测试框架发现但自动跳过外部依赖用例。
- 设置 smoke 环境变量后可执行对应 Redis/OAuth/Ops smoke。
- smoke 文档记录环境变量、命令、输出位置和禁止写入仓库的敏感信息。
- 任务完成后回写实现结果、验证情况和遗留问题。

## 实现结果

- 新增 `SmokeHarnessSupport`，统一处理 `XAG_SMOKE_*` 开关、环境变量 fallback 和本地 smoke 报告输出。
- 新增 `RedisRuntimeStoreSmokeHarnessTests`，通过 `XAG_SMOKE_REDIS=true` 可连接真实 Redis，验证共享 rate window、circuit state、half-open lock 和定点 reset。
- 新增 `SocialOAuthSmokeHarnessTests`，通过 `XAG_SMOKE_OAUTH_MOCK=true` 可执行 Google/GitHub/QQ/WeChat/Meta/X provider mock contract。
- 新增 `OpsMaintenanceSmokeHarnessTests`，通过 `XAG_SMOKE_OPS_DRY_RUN=true` 可执行 maintenance dry-run smoke。
- 新增 [testing-smoke-harness](../testing-smoke-harness.md)，并在 [testing-baseline](../testing-baseline.md) 中加入可选 smoke 入口。

## 验证情况

- 默认外部依赖关闭时，已通过：
  - `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.routing.RedisRuntimeStoreSmokeHarnessTests" --tests "com.prodigalgal.xaigateway.portal.application.SocialOAuthSmokeHarnessTests" --tests "com.prodigalgal.xaigateway.admin.application.OpsMaintenanceSmokeHarnessTests"`
- 开启本地 mock smoke 后，已通过：
  - `$env:XAG_SMOKE_OAUTH_MOCK='true'; $env:XAG_SMOKE_OPS_DRY_RUN='true'; .\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.portal.application.SocialOAuthSmokeHarnessTests" --tests "com.prodigalgal.xaigateway.admin.application.OpsMaintenanceSmokeHarnessTests"`
- Redis VM 启动后，已通过真实 Redis smoke：
  - `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.routing.RedisRuntimeStoreSmokeHarnessTests"`
- 已生成本地 smoke 报告：
  - `build/reports/xag-smoke/social-oauth-mock.md`
  - `build/reports/xag-smoke/ops-maintenance-dry-run.md`
  - `build/reports/xag-smoke/redis-runtime-store.md`
- 已对本轮改动执行 `git diff --check`，未发现空白错误。

## 遗留问题

- 真实第三方 OAuth 线上 smoke 仍按 [testing-social-oauth-smoke](../testing-social-oauth-smoke.md) 手工执行，不进入默认 CI。
