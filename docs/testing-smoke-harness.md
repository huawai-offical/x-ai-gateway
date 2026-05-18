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

## OpenAI Direct Credential Permission Smoke

关联任务：[TASK-20260516-005](../tasks/done/TASK-20260516-005-openai-direct-key-vault-permission-smoke.md)

OpenAI Direct 真实 smoke 复用后台 `UpstreamCredential` 加密凭证作为 key vault，不再要求把真实 API key 写入脚本、仓库或测试报告。本切片只做低成本权限探测：

- dry-run 默认返回 `GET /v1/models` request preview、credential fingerprint 与执行前置状态，不解密凭证、不访问 OpenAI。
- live probe 仅在显式 `dryRun=false` 时执行，使用已入库 `OPENAI_DIRECT` credential 的明文解密值发起 `GET /v1/models`。
- 可选 `OpenAI-Organization` 与 `OpenAI-Project` header 只从请求参数读取；响应与持久化结果只保留脱敏 marker。
- 结果分类沿用 `PASS`、`FAIL`、`SKIPPED`、`UNSUPPORTED`、`NO_PERMISSION`、`BUDGET_BLOCKED`，便于后续 Chat、Responses、Files、Batches、Vector Stores、Realtime client secret smoke runner 复用。

手工 live probe 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/openai-direct/smoke" `
  -ContentType "application/json" `
  -Body '{"dryRun":false,"timeoutSeconds":10}'
```

真实 key 只应通过后台凭证创建/更新接口入库；报告和日志中只能出现 credential id、fingerprint、HTTP status、request id、分类与脱敏错误摘要。

### 资源族分类 Smoke

关联任务：[TASK-20260516-006](../tasks/done/TASK-20260516-006-openai-direct-resource-family-smoke-runner.md)

资源族 smoke 在同一 credential key vault 地基上输出 Chat、Responses、Files、Batches、Vector Stores、Realtime client secret 六类分类项。默认边界：

- dry-run：返回六类 request preview 和 summary，不解密、不访问 OpenAI。
- live：只执行低风险只读 list probe：
  - `GET /v1/files?limit=1`
  - `GET /v1/batches?limit=1`
  - `GET /v1/vector_stores?limit=1`
- Chat Completions 与 Responses 属于 billable generation probe，默认 `BUDGET_BLOCKED / BILLABLE_PROBE_BLOCKED`。
- Realtime client secret 属于写操作 probe，默认 `BUDGET_BLOCKED / WRITE_PROBE_BLOCKED`。
- 只有显式设置 `allowBillableProbes=true` 时，Chat/Responses 才会执行最小输出 token 的 POST probe：
  - `POST /v1/chat/completions`，默认 `model=gpt-4o-mini`、`max_completion_tokens=1`、`store=false`。
  - `POST /v1/responses`，默认 `model=gpt-4o-mini`、`max_output_tokens=1`、`store=false`。
- 只有显式设置 `allowWriteProbes=true` 时，Realtime client secret 才会执行短 TTL text-only session probe：
  - `POST /v1/realtime/client_secrets`，默认 `model=gpt-realtime-mini`、`output_modalities=["text"]`、`max_output_tokens=1`、`expires_after.seconds=60`。
- 401/403 归类为 `NO_PERMISSION`，429/rate/quota/limit 归类为 `BUDGET_BLOCKED`，404 归类为 `UNSUPPORTED`。

手工 dry-run 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/openai-direct/resource-smoke" `
  -ContentType "application/json" `
  -Body '{"dryRun":true}'
```

受控 live 只读 probe 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/openai-direct/resource-smoke" `
  -ContentType "application/json" `
  -Body '{"dryRun":false,"resourceFamilies":["files","batches","vector_stores"],"timeoutSeconds":10}'
```

显式允许最小 billable/write probe 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/openai-direct/resource-smoke" `
  -ContentType "application/json" `
  -Body '{"dryRun":false,"resourceFamilies":["chat_completions","responses","realtime_client_secret"],"allowBillableProbes":true,"allowWriteProbes":true,"timeoutSeconds":10}'
```

这个示例会真实访问上游并可能产生极低成本或写入短期 ephemeral client secret；批量 smoke 默认不得带这两个 allow flag。

### Certification 与脱敏 Fixture 基线

关联任务：[TASK-20260516-007](../tasks/done/TASK-20260516-007-openai-direct-smoke-certification-fixture.md)

Certification endpoint 会执行同一资源族 smoke，并把结果折叠为可审计的 certification report：

- `certificationStatus` 支持 `DRY_RUN`、`CERTIFIED`、`PARTIAL_CERTIFIED`、`NO_PERMISSION`、`BUDGET_BLOCKED`、`UNSUPPORTED`、`FAILED`。
- `fixtureSnapshots` 按资源族保留 method/path、classification、skipped reason、HTTP status、request id、duration、failure 摘要、安全 evidence 与 request preview。
- request preview、fixture snapshot 与 live metadata 会递归脱敏 Authorization、token、secret、api key、organization/project header，以及 `Bearer ...`、`sk-...`、`org-*`、`proj-*` 文本形态。
- `recordReplayFixture` 会把脱敏后的 fixture snapshots 包装为版本化 record/replay bundle，包含 `schemaVersion`、`replayMode`、`providerType`、`baseUrl`、`certificationStatus`、`dryRun`、`summary`、`replayPolicy` 与 `fixtures`。
- `replayPolicy.network=disabled_by_default`、`billableOperations=replay_only`、`writeOperations=replay_only`，后续 CI 或人工脚本必须显式选择 replay，不得因 fixture 存在而自动访问真实上游。
- dry-run 只返回预览，不解密、不访问上游、不写入 credential metadata。
- live certification 会把安全摘要和 `recordReplayFixture` 写入 `credentialMetadataJson.openai_direct_smoke_certification`，便于后台和受控 CI 读取最近一次认证结果。

仓库内脱敏 sample fixture：

```text
src/test/resources/conformance/openai-direct-smoke-record-replay-fixture.sample.json
```

### Record/Replay CI 校验器

关联任务：[TASK-20260517-001](../tasks/done/TASK-20260517-001-openai-direct-smoke-record-replay-ci-verifier.md)

`OpenAiDirectSmokeRecordReplayFixtureVerifier` 是默认离线的 CI 校验器，只读取本地 `recordReplayFixture` JSON，不访问真实 OpenAI，也不会触发 billable 或 write probe。校验范围包括：

- `schemaVersion`、`replayMode`、`providerType`、`recordedAt`、`certificationStatus` 与 `summary`。
- `replayPolicy.network=disabled_by_default`、`billableOperations=replay_only`、`writeOperations=replay_only`、`secretMaterial=redacted`。
- 每个 fixture 的 `resourceFamily`、`status`、`classification`、`method`、`path`、`billable`、`writeOperation`、`evidence` 和 `requestPreview`。
- `summary` 与 fixture classification 计数必须一致。
- 递归扫描字符串值，拦截 `Bearer sk-`、`sk-`、`AIza`、真实 `org-*`、真实 `proj-*` 和常见第三方 token 形态。

CI 聚焦命令：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.OpenAiDirectSmokeRecordReplayFixtureVerifierTests"
```

手工 dry-run certification 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/openai-direct/resource-smoke/certification" `
  -ContentType "application/json" `
  -Body '{"dryRun":true}'
```

受控 live 只读 certification 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/openai-direct/resource-smoke/certification" `
  -ContentType "application/json" `
  -Body '{"dryRun":false,"resourceFamilies":["files","batches","vector_stores"],"timeoutSeconds":10}'
```

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
