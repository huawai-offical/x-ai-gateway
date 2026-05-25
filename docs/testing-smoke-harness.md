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
- 结果分类沿用 `PASS`、`FAIL`、`SKIPPED`、`UNSUPPORTED`、`NO_PERMISSION`、`BUDGET_BLOCKED`，便于后续 Chat、Responses、Files、Vector Stores、Realtime client secret smoke runner 复用。

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

资源族 smoke 在同一 credential key vault 地基上输出 Chat、Responses、Files、Vector Stores、Realtime client secret 五类分类项。默认边界：

- dry-run：返回五类 request preview 和 summary，不解密、不访问 OpenAI。
- live：只执行低风险只读 list probe：
  - `GET /v1/files?limit=1`
  - `GET /v1/vector_stores?limit=1`
- Chat Completions 与 Responses 属于 billable generation probe，默认 `BUDGET_BLOCKED / BILLABLE_PROBE_BLOCKED`。
- Realtime client secret 属于写操作 probe，默认 `BUDGET_BLOCKED / WRITE_PROBE_BLOCKED`。
- 只有显式设置 `allowBillableProbes=true` 时，Chat/Responses 才会执行最小输出 token 的 POST probe：
  - `POST /v1/chat/completions`，默认 `model=gpt-4o-mini`、`max_completion_tokens=1`、`store=false`。
  - `POST /v1/responses`，默认 `model=gpt-4o-mini`、`max_output_tokens=1`、`store=false`。
- 只有显式设置 `allowWriteProbes=true` 时，Realtime client secret 才会执行短 TTL text-only session probe：
  - `POST /v1/realtime/client_secrets`，默认 `model=gpt-realtime-mini`、`output_modalities=["text"]`、`max_output_tokens=1`、`expires_after.seconds=60`。
- 401/403 归类为 `NO_PERMISSION`，429/rate/quota/limit 归类为 `BUDGET_BLOCKED`，404 归类为 `UNSUPPORTED`。
- Anthropic、Gemini、Vertex、Codex 的真实 smoke 也按功能性服务 API 与各自 native/profile 边界收紧；不为 Anthropic message batches、Gemini/Vertex batch prediction、tuning、evals、pipeline/job/admin 或非 Responses Codex 内部接口保留 smoke 预算。

### 核心 Provider Native / Provider-specific 功能性 Smoke 范围

关联需求：[REQ-20260519-001](requirements/REQ-20260519-001-functional-real-smoke-gemini-mimo.md)
关联任务：[TASK-20260519-001](../tasks/done/TASK-20260519-001-functional-real-smoke-gemini-mimo.md)

当前没有 OpenAI Direct key 时，真实 smoke 可以先使用 Google Gemini key 与小米 MiMo key，但只用于核心 provider native 或 provider-specific 功能性服务 API。当前项目 MiMo 预设与本地协议入口使用 token-plan 地址：MiMo provider-specific OpenAI-compatible Base URL 为 `https://token-plan-sgp.xiaomimimo.com/v1`，MiMo provider-specific Anthropic-compatible Base URL 为 `https://token-plan-sgp.xiaomimimo.com/anthropic`；这证明它可用于 MiMo 对话协议兼容 smoke，不证明 generic OpenAI-compatible 或 OpenAI Direct 全量资源族。

`mimo_openai` 与 `mimo_anthropic` 是 MiMo provider-specific profile，不是 generic fallback。`openai_compatible` / `anthropic_compatible` 只能作为兼容旧请求的 alias 使用：只有命中 MiMo baseUrl 或明确 MiMo profile 时才归一到 `XIAOMI_MIMO_*`；其它自有厂商 compatible 入口保持自己的 generic protocol 语义，不得冒充 MiMo official smoke。Dify、OpenRouter、Together、Fireworks、SiliconFlow 与 generic compatible 不在默认 official smoke 预算中；如需纳入，必须单独建立 provider-specific 范围、凭证、预算和验收边界。

| Provider | Protocol | Default base URL | Auth strategy | 默认模型 | 默认 family | 默认 live 行为 | 明确排除 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Gemini | Gemini native | `https://generativelanguage.googleapis.com` | `API_KEY_QUERY` / profile 驱动 | `gemini-2.5-flash` | `generate_content`、`stream_generate_content`、`tool_calling` | dry-run；live 需 `allowLive=true` 与 key 引用 | batch prediction、pipeline/job/admin、tuning |
| MiMo | MiMo provider-specific OpenAI-compatible | `https://token-plan-sgp.xiaomimimo.com/v1` | profile 驱动，优先允许 `api-key`，兼容 SDK-style key 配置 | `mimo-v2-pro` | `chat_completions`、`chat_streaming`、`chat_tools` | dry-run；billable live 需显式 allow | generic OpenAI-compatible fallback、Responses、Files、Uploads、Vector Stores、Realtime client secret、Batches、Fine-tuning、Evals、Admin |
| MiMo | MiMo provider-specific Anthropic-compatible | `https://token-plan-sgp.xiaomimimo.com/anthropic` | profile 驱动，`api-key` / Anthropic-compatible header 由站点档案声明 | `mimo-v2-pro` | `messages`、`messages_streaming`、`tool_use` | dry-run；billable live 需显式 allow | generic Anthropic-compatible fallback、Message Batches、Admin、Files、Evals |
| Cohere | Cohere native | `https://api.cohere.ai` | Bearer | `embed-v4.0`、`rerank-v3.5` | `embeddings`、`rerank` | dry-run；live 需双 gate 与 `COHERE_API_KEY` / `XAI_GATEWAY_COHERE_API_KEY` | chat、files、uploads、OpenAI-compatible generic fallback |
| Jina | Jina native | `https://api.jina.ai` | Bearer | `jina-embeddings-v3`、`jina-reranker-v2-base-multilingual` | `embeddings`、`rerank` | dry-run；live 需双 gate 与 `JINA_API_KEY` / `XAI_GATEWAY_JINA_API_KEY` | chat、files、uploads、OpenAI-compatible generic fallback |

默认分类规则：

- 未在矩阵中的 family 不进入默认列表；如果用户显式请求，返回 `UNSUPPORTED`，`skippedReason=OUT_OF_FUNCTIONAL_API_SCOPE`。
- 缺少真实 key 或未开启 live，返回 `SKIPPED`，`skippedReason=DRY_RUN` 或 `NO_CREDENTIAL`。
- billable generation 默认返回 `BUDGET_BLOCKED`，只有显式 budget/allow flag 通过后才发起 live。
- provider auth header、base URL、model 与 protocol 必须来自 credential/site profile 或 smoke request，不能硬编码 OpenAI Direct。
- MiMo compatible 口径必须记录为 provider-specific profile；`mimo_openai` / `mimo_anthropic` 是首选请求值，`openai_compatible` / `anthropic_compatible` 仅保留为旧请求 alias，且只有 MiMo baseUrl/profile 才会归一到 `XIAOMI_MIMO_*`。
- Dify/OpenRouter/Together/Fireworks/SiliconFlow/generic 的 fixture 或 live probe 不进入默认 official smoke；即使它们也暴露 OpenAI-compatible 端点，也不能被 sample 或 verifier 当作核心 provider。
- record/replay fixture 必须包含 `provider`、`protocol`、`resourceFamily`、`model`、`baseUrlHost`、`classification`、`skippedReason`，并递归脱敏 key、Authorization、`api-key`、`x-api-key`、organization/project。

已实现入口：

```text
POST /admin/credentials/{id}/functional-provider/smoke
```

请求字段：

- `protocol`：可选，支持 `gemini_native`、`mimo_openai` / `openai_compatible`、`mimo_anthropic` / `anthropic_compatible`；`mimo_openai` / `mimo_anthropic` 是 MiMo provider-specific profile，generic alias 只是兼容旧请求，不表示默认 official smoke fallback，未传时按 credential `providerType` 与 site profile 推断。
- `baseUrl`：可选，覆盖 credential base URL；MiMo OpenAI-compatible 可传 `https://token-plan-sgp.xiaomimimo.com/v1`，runner 会生成 `/v1/chat/completions`。
- `model`：可选，默认 Gemini `gemini-2.5-flash`、MiMo `mimo-v2-pro`。
- `resourceFamilies`：可选；未传时使用矩阵默认 family，显式传入范围外 family 时返回 `UNSUPPORTED`。
- `dryRun`：默认 `true`；`false` 表示请求 live，但仍需 `allowLive=true`。
- `allowLive`：默认 `false`；未开启时返回 `SKIPPED / LIVE_NOT_ALLOWED`，不会解密 key 或访问上游。
- `allowBillableProbes`：默认 `false`；live generation 未开启时返回 `BUDGET_BLOCKED / BILLABLE_PROBE_BLOCKED`。

MiMo OpenAI-compatible dry-run 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/functional-provider/smoke" `
  -ContentType "application/json" `
  -Body '{"dryRun":true,"protocol":"mimo_openai","resourceFamilies":["chat_completions","chat_tools"]}'
```

MiMo Anthropic-compatible dry-run 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/functional-provider/smoke" `
  -ContentType "application/json" `
  -Body '{"dryRun":true,"protocol":"mimo_anthropic","resourceFamilies":["messages","tool_use"]}'
```

Gemini native dry-run 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/functional-provider/smoke" `
  -ContentType "application/json" `
  -Body '{"dryRun":true,"protocol":"gemini_native","resourceFamilies":["generate_content","tool_calling"]}'
```

显式允许最小 billable live probe 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/functional-provider/smoke" `
  -ContentType "application/json" `
  -Body '{"dryRun":false,"allowLive":true,"allowBillableProbes":true,"protocol":"mimo_openai","resourceFamilies":["chat_completions"],"timeoutSeconds":10}'
```

这个示例会真实访问上游并可能产生极低成本；批量 smoke 默认不得带 `allowLive=true` 与 `allowBillableProbes=true`。

Cohere/Jina native live gate 命令：

```powershell
$env:XAI_GATEWAY_FUNCTIONAL_PROVIDER_LIVE_SMOKE = "true"
$env:XAI_GATEWAY_ALLOW_BILLABLE_SMOKE = "true"
$env:COHERE_API_KEY = "<cohere-key>"
$env:JINA_API_KEY = "<jina-key>"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeLiveGateTests"
```

这个测试默认不会访问网络：未设置 `XAI_GATEWAY_FUNCTIONAL_PROVIDER_LIVE_SMOKE=true` 和 `XAI_GATEWAY_ALLOW_BILLABLE_SMOKE=true` 时，JUnit assumption 会把用例标记为 skipped；缺少 `COHERE_API_KEY` / `XAI_GATEWAY_COHERE_API_KEY` 或 `JINA_API_KEY` / `XAI_GATEWAY_JINA_API_KEY` 时，对应 provider 也会 skipped。只有双 gate 与真实 key 同时存在时，测试才会访问 Cohere `/v2/embed`、`/v2/rerank` 和 Jina `/v1/embeddings`、`/v1/rerank`，并要求 record/replay fixture 通过 `FunctionalProviderSmokeRecordReplayFixtureVerifier`。这条 live gate 不能用 dry-run、sample fixture 或本地模拟结果替代。

### Functional Provider Certification 与脱敏 Fixture

关联任务：[TASK-20260519-001-03](../tasks/done/TASK-20260519-001-03-smoke-record-replay-redaction-budget.md)

功能性 provider certification endpoint 会执行同一 Gemini/MiMo smoke，并把结果折叠为 provider-aware record/replay bundle：

```text
POST /admin/credentials/{id}/functional-provider/smoke/certification
```

fixture schema：

```text
2026-05-19.functional-provider-smoke-record-replay.v1
```

仓库内脱敏 sample fixture：

```text
src/test/resources/conformance/functional-provider-smoke-record-replay-fixture.sample.json
```

功能性 provider fixture 与 OpenAI Direct fixture 分离，原因是它必须记录 provider-specific provider/protocol/model，并允许 Gemini `/v1beta/models/...:generateContent`、MiMo provider-specific OpenAI-compatible `/v1/chat/completions`、MiMo provider-specific Anthropic-compatible `/v1/messages`、DeepSeek provider-specific OpenAI-compatible `/v1/chat/completions`、xAI provider-specific OpenAI-compatible `/v1/chat/completions`、Cohere native `/v2/embed` / `/v2/rerank`、Jina native `/v1/embeddings` / `/v1/rerank` 等路径。MiMo、DeepSeek、xAI、Cohere、Jina 离线 fixture 使用 `XIAOMI_MIMO`、`DEEPSEEK`、`XAI`、`COHERE`、`JINA` 以及对应 provider-specific protocol 字符串，不使用顶层 `OPENAI_COMPATIBLE` 泛名。

- `network=disabled_by_default`
- `billableOperations=replay_only`
- `writeOperations=replay_only`
- `secretMaterial=redacted`
- `fixtureSource=functional_provider_smoke_certification`
- `liveExecutionRequiresAllowLive=true`
- `billableExecutionRequiresAllowBillableProbes=true`

功能性 provider verifier `FunctionalProviderSmokeRecordReplayFixtureVerifier` 是离线校验器，只读取本地 fixture，不访问 Gemini、MiMo、DeepSeek 或 xAI。校验范围包括：

- 顶层 `schemaVersion`、`providerType`、`protocol`、`baseUrlHost`、`recordedAt`、`certificationStatus`、`summary`。
- 每个 fixture 的 `providerType`、`protocol`、`resourceFamily`、`model`、`classification`、`skippedReason`、`method`、`path`、`billable`、`writeOperation`、`evidence` 和 `requestPreview`。
- path 必须与 provider-specific protocol 匹配：Gemini native 只允许 GenerateContent 路径，`XIAOMI_MIMO_OPENAI_COMPATIBLE`、`DEEPSEEK_OPENAI_COMPATIBLE`、`XAI_OPENAI_COMPATIBLE` 只允许 Chat Completions，`XIAOMI_MIMO_ANTHROPIC_COMPATIBLE` 只允许 Messages。
- Cohere/Jina native fixture 只允许 `EMBEDDINGS` / `RERANK` 作为成功 family；chat、files、uploads 等非 embed/rerank family 只能记录 `UNSUPPORTED`，不得记录为 PASS。
- `DIFY`、`OPENROUTER`、`TOGETHER`、`FIREWORKS`、`SILICONFLOW`、`OPENAI_COMPATIBLE_GENERIC` 与顶层 `OPENAI_COMPATIBLE` 不是 functional provider official smoke 的允许 fixture provider/protocol。
- `summary` 与 fixture classification 计数必须一致。
- 递归扫描并拦截未脱敏 `Bearer ...`、`sk-...`、`AIza...`、`api-key=...`、`x-goog-api-key=...`、真实 `org-*`、真实 `proj-*` 和常见第三方 token。

CI 聚焦命令：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests"
```

手工 dry-run certification 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/functional-provider/smoke/certification" `
  -ContentType "application/json" `
  -Body '{"dryRun":true,"protocol":"mimo_openai"}'
```

受控 live certification 示例：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8080/admin/credentials/<credentialId>/functional-provider/smoke/certification" `
  -ContentType "application/json" `
  -Body '{"dryRun":false,"allowLive":true,"allowBillableProbes":true,"protocol":"mimo_openai","resourceFamilies":["chat_completions"],"timeoutSeconds":10}'
```

### Codex Responses Smoke Record/Replay

关联任务：[TASK-20260519-002-02](../tasks/done/TASK-20260519-002-02-codex-smoke-record-replay-priority.md)

Codex 官方账号 smoke 只覆盖 `/backend-api/codex/responses`，不扩展非 Responses 内部 API。`codexResponsesSmoke` 响应与账号 `lastRefreshResultJson` 会包含 `recordReplayFixture`：

- `schemaVersion=2026-05-19.codex-responses-smoke-record-replay.v1`。
- `providerType=CODEX_OAUTH`、`protocol=codex-responses`。
- `replayPolicy.network=disabled_by_default`、`billableOperations=replay_only`、`writeOperations=replay_only`、`secretMaterial=redacted`。
- `fixtures[0].resourceFamily=codex_responses`，保留 classification、skipped reason、method/path、model、billable/writeOperation、evidence 与 request preview。
- dry-run fixture 允许作为安全证据，但不表示真实线上 smoke 已通过。

仓库内脱敏 sample fixture：

```text
src/test/resources/conformance/codex-responses-smoke-record-replay-fixture.sample.json
```

离线 verifier：

```text
CodexResponsesSmokeRecordReplayFixtureVerifier
```

该 verifier 只读取本地 fixture，不访问 ChatGPT/OpenAI，不触发 billable 或 write probe；校验 schema、replay policy、summary 计数、Codex Responses path 和常见 token 泄漏。

恢复测试后优先执行：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.OfficialAccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CodexResponsesSmokeRecordReplayFixtureVerifierTests"
```

本命令仍只验证本地服务逻辑与离线 fixture，不执行真实 Codex/OpenAI 网络 smoke。

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
  -Body '{"dryRun":false,"resourceFamilies":["files","vector_stores"],"timeoutSeconds":10}'
```

## 输出位置

开启对应 smoke 后，测试会写入：

```text
build/reports/xag-smoke/
```

这些报告是本地运行产物，不需要提交到仓库。

## 本地验证记录

- 2026-05-05：Redis VM 启动后，真实 Redis smoke 已通过，报告输出为 `build/reports/xag-smoke/redis-runtime-store.md`。
- 2026-05-19：Gemini/MiMo 功能性 provider smoke runner 已通过聚焦测试 `FunctionalProviderSmokeHttpClientTests`、`CredentialAdminServiceTests`，并通过全量 `.\gradlew.bat test`。
- 2026-05-19：Gemini/MiMo 功能性 provider certification、sample fixture 与离线 verifier 已通过 `FunctionalProviderSmokeCertificationServiceTests`、`FunctionalProviderSmokeRecordReplayFixtureVerifierTests` 和 `CredentialAdminServiceTests` 聚焦验证。
- 2026-05-19：Codex Responses smoke 已新增 record/replay fixture 输出、脱敏 sample fixture 与离线 verifier；`.\gradlew.bat compileJava compileTestJava -x test` 通过；按用户要求本轮暂未运行测试。

## 敏感信息约束

- 不提交 Redis 密码、OAuth clientSecret、access token、刷新 token。
- 不提交真实测试账号邮箱、头像、昵称等个人资料。
- 报告如需记录账号标识，只能记录脱敏后的 provider 与外部 subject hash。
