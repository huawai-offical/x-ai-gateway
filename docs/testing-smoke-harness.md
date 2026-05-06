# Redis/OAuth/Ops 可选 Smoke Harness

状态：Active
关联需求：[REQ-20260505-003](requirements/REQ-20260505-003-smoke-harness-hardening.md)
关联任务：[TASK-20260505-006](../tasks/done/TASK-20260505-006-redis-oauth-ops-smoke-harness.md)

## 目标

本 smoke harness 用于在需要时验证真实 Redis、社交 OAuth mock contract 和 Ops maintenance dry-run。默认情况下，相关测试会被 JUnit 发现但自动跳过，不影响无外部依赖的 CI。

## 执行命令

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.routing.RedisRuntimeStoreSmokeHarnessTests" --tests "com.prodigalgal.xaigateway.portal.application.SocialOAuthSmokeHarnessTests" --tests "com.prodigalgal.xaigateway.admin.application.OpsMaintenanceSmokeHarnessTests"
```

## Redis Runtime Store Smoke

开启方式：

```powershell
$env:XAG_SMOKE_REDIS="true"
$env:XAG_SMOKE_REDIS_HOST="localhost"
$env:XAG_SMOKE_REDIS_PORT="6379"
$env:XAG_SMOKE_REDIS_PASSWORD=""
$env:XAG_SMOKE_REDIS_DATABASE="0"
```

如果使用仓库默认配置，可从 `spring.data.redis` 配置块读取同一组 host、port、password、database 后赋给 `XAG_SMOKE_REDIS_*`。注意不要从 `spring.datasource.password` 误取数据库密码。

验证内容：

- 两个 `RedisRoutingPolicyRuntimeStore` 实例共享同一 rate window。
- circuit state 可以跨实例读取。
- half-open probe lock 防止多实例同时放量。
- 指定 `runtimeKey` reset 可清理 rate、circuit 和 half-open lock。

## OAuth Mock Contract Smoke

开启方式：

```powershell
$env:XAG_SMOKE_OAUTH_MOCK="true"
```

验证内容：

- Google/GitHub/QQ/WeChat/Meta/X provider client 都能完成 mock token exchange。
- provider profile 标准化后的 `externalSubject` 与 displayName 可验证。
- X provider 使用 PKCE code verifier 路径。

真实线上 provider smoke 仍按 [testing-social-oauth-smoke](testing-social-oauth-smoke.md) 手工执行，不能把真实 secret 或 token 写入仓库。

## Ops Maintenance Dry-run Smoke

开启方式：

```powershell
$env:XAG_SMOKE_OPS_DRY_RUN="true"
```

验证内容：

- `PRECHECK`、`UPGRADE_CHECK`、`ROLLBACK_PLAN` dry-run 都能完成。
- 每次运行生成 detail、summary、checks 和 checksum。
- `OpsAuditService.record` 被调用，便于后续接入真实审计查询。

## 输出位置

开启对应 smoke 后，测试会写入：

```text
build/reports/xag-smoke/
```

这些报告是本地运行产物，不需要提交到仓库。

## 本地验证记录

- 2026-05-05：Redis VM 启动后，真实 Redis smoke 已通过，报告输出为 `build/reports/xag-smoke/redis-runtime-store.md`。

## 敏感信息约束

- 不提交 Redis 密码、OAuth clientSecret、access token、刷新 token。
- 不提交真实测试账号邮箱、头像、昵称等个人资料。
- 报告如需记录账号标识，只能记录脱敏后的 provider 与外部 subject hash。
