# TASK-20260514-031 OpenAI 真实 Smoke 与认证成本防护

状态：Backlog
优先级：Critical
类型：子任务
父任务：[TASK-20260514-016](TASK-20260514-016-functional-service-api-coverage-parent.md)
上游来源：[TASK-20260514-015](TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md)

## 背景

对话与 tools 功能性 API 不能只靠 mock。OpenAI Direct 的 Chat、Responses、Models、Files/Uploads、Vector Stores file_search 支撑、Realtime client secret 等需要真实 key、成本预算和安全隔离。Fine-tuning、Batches、Evals、Videos、Administration 等非核心 API 已不再作为 smoke 目标。

2026-05-19 用户要求 Codex 相关任务提升为最高优先。Codex smoke、record/replay 与成本防护复核由 [TASK-20260519-002-02](../done/TASK-20260519-002-02-codex-smoke-record-replay-priority.md) 作为第二执行切片承接；该切片已按当前测试策略归档，自动化测试待用户恢复测试后补跑。

## 目标

- 建立 OpenAI real smoke harness，支持按资源族选择执行。
- 建立 secret vault、权限探测、成本预算、速率限制和 skipped reason。
- 支持 record/replay，把成功响应脱敏后作为 conformance fixture。
- 在 admin/CI 中输出 coverage certification 报告。

## 非目标

- 不把用户提供的真实 key 写入代码或仓库。
- 不默认执行高成本写操作。

## 输入

- OpenAI API key、organization/project 权限。
- 对话/tools coverage matrix。
- 现有 provider smoke/pricing sync 机制。

## 输出

- Real smoke runner。
- 成本预算和权限分类。
- 脱敏 fixture、认证报告和失败分类。

## 影响范围

- Test harness、admin smoke UI、credential storage、audit logs、conformance fixtures、docs。

## 依赖

- `TASK-20260514-030` 横切协议兼容。
- 真实 key 和组织权限。

## 风险

- 高成本 API 被误触发。
- 真实响应可能包含敏感信息，必须脱敏。
- Rate limit 或权限不足不能误判为功能失败。

## 验收标准

- Smoke 支持 `PASS/FAIL/SKIPPED/UNSUPPORTED/NO_PERMISSION/BUDGET_BLOCKED` 分类。
- 所有真实请求有审计、成本上限和脱敏记录。
- 至少覆盖 Chat、Responses、Files/Uploads、Vector Stores file_search 支撑、Realtime client secret 的 smoke 分类。

## 测试边界

- Harness unit tests。
- Credential redaction tests。
- 真实 smoke 手动或受控 CI 执行。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 已完成切片

- [TASK-20260515-016 OpenAI/Codex Real Smoke 分类与预算阻断基线](../done/TASK-20260515-016-openai-codex-real-smoke-classification-budget-guard.md)：Codex App API responses smoke 已输出标准 `classification` 与 `skippedReason`；usage probe 在额度、速率、权限阻断时不继续发起真实 responses POST；账号池详情页已展示标准分类。
- [TASK-20260516-005 OpenAI Direct Key Vault 权限探测与 Secret 引用 Smoke](../done/TASK-20260516-005-openai-direct-key-vault-permission-smoke.md)：后台 credential smoke 已可按 `credentialId` 引用 `OPENAI_DIRECT` 加密凭证；dry-run 不解密、不访问上游；live probe 仅执行低成本 `GET /v1/models`，并对权限、rate limit、非 OpenAI Direct、脱敏错误摘要做标准分类。
- [TASK-20260516-006 OpenAI Direct 资源族 Smoke Runner 分类骨架](../done/TASK-20260516-006-openai-direct-resource-family-smoke-runner.md)：资源族 smoke 历史上覆盖 Chat、Responses、Files、Batches、Vector Stores、Realtime client secret 六类分类项；范围收窄后 Batches 不再作为当前 smoke 目标。
- [TASK-20260516-007 OpenAI Direct Smoke Certification 与脱敏 Fixture 基线](../done/TASK-20260516-007-openai-direct-smoke-certification-fixture.md)：资源族 smoke 已可生成 certification report 与脱敏 fixture snapshot；live certification 会把安全摘要写入 `credentialMetadataJson.openai_direct_smoke_certification`，dry-run 不写入 metadata。
- [TASK-20260516-009 OpenAI Direct 显式 Billable/Write Smoke Probe](../done/TASK-20260516-009-openai-direct-explicit-billable-write-smoke-probes.md)：Chat/Responses billable generation 与 Realtime client secret 写操作已支持显式 allow flag；默认仍保护阻断，显式开启时使用最小 token、短 TTL、text-only payload，并保留脱敏 preview/evidence。
- [TASK-20260516-017 OpenAI Direct Smoke Record/Replay Fixture 固化](../done/TASK-20260516-017-openai-direct-smoke-record-replay-fixture.md)：Certification response 与 live metadata 已包含版本化 `recordReplayFixture`，仓库内新增脱敏 sample fixture，明确 network disabled、billable/write replay-only 策略。
- [TASK-20260517-001 OpenAI Direct Smoke Record/Replay CI 校验器](../done/TASK-20260517-001-openai-direct-smoke-record-replay-ci-verifier.md)：新增离线 fixture verifier，CI 可校验 schema、replay-only policy、summary 计数、fixture 必填字段和未脱敏 secret，默认不访问真实 OpenAI。

## 剩余切片

- 真实线上 OpenAI Direct smoke 仍需由受控环境提供真实 key、预算和手工/CI 开关；仓库默认测试继续使用本地 mock server 与离线 record/replay verifier。
- [TASK-20260519-001 Gemini 与 MiMo 功能性服务 API 真实 Smoke](../done/TASK-20260519-001-functional-real-smoke-gemini-mimo.md)：在当前无 OpenAI key 的约束下，先用 Gemini key 与 MiMo OpenAI/Anthropic-compatible key 建立功能性服务 smoke；严格排除 Fine-tuning、Batches、Evals、Admin 与 provider batch/admin。
- [TASK-20260519-002-02 Codex Smoke、Record/Replay 与成本防护复核](../done/TASK-20260519-002-02-codex-smoke-record-replay-priority.md)：在 Codex 最高优先队列中复核 Codex smoke 目标、record/replay、脱敏和预算阻断，默认不触发真实网络或成本；已按当前测试策略归档。
