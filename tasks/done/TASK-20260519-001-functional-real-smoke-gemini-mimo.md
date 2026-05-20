# TASK-20260519-001 Gemini 与 MiMo 功能性服务 API 真实 Smoke

状态：Done
优先级：Critical
类型：父任务
上游来源：[REQ-20260519-001](../../docs/requirements/REQ-20260519-001-functional-real-smoke-gemini-mimo.md)、[TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)

## 背景

当前可用真实 key 不是 OpenAI Direct，而是 Google Gemini 与小米 MiMo。MiMo 官方文档显示它支持 OpenAI-compatible 与 Anthropic-compatible API formats，适合作为对话协议 smoke 的替代凭证；但产品范围已经明确排除 Fine-tuning、Batches、Evals、Admin 等非核心 API，因此 smoke harness 需要先收紧范围，再接入真实 key。

## 目标

- 建立 Gemini/MiMo 功能性服务 API smoke 范围矩阵。
- 设计并实现 provider-aware smoke 配置：provider、protocol、base URL、auth strategy、model、family、live allow flag。
- 保证 MiMo OpenAI-compatible 只覆盖 Chat/streaming/tools；MiMo Anthropic-compatible 只覆盖 Messages/streaming/tool use；Gemini 只覆盖 GenerateContent/tools。
- 输出标准分类、脱敏 evidence、record/replay fixture 和 skipped reason。

## 非目标

- 不把 MiMo 当作 OpenAI Direct 全量 key。
- 不 smoke OpenAI `/v1/batches`、Fine-tuning、Evals、Admin、Anthropic Message Batches、Gemini batch prediction。
- 不提交真实 key 或真实敏感响应。

## 输入

- `REQ-20260519-001`
- MiMo 官方文档：OpenAI-compatible base URL、Anthropic-compatible base URL、function calling 与 streaming 支持说明。
- 现有 `OpenAiDirectResourceSmokeHttpClient`、Credential smoke/certification 服务、record/replay verifier。

## 输出

- 更新后的 smoke family scope matrix。
- Gemini/MiMo smoke runner 或 provider adapter 设计与实现。
- 单元测试、离线 fixture、文档回写。

## 影响范围

- `admin/application/*Smoke*`
- `admin/api/*Smoke*`
- provider site auth strategy 与 catalog smoke notes
- `docs/testing-smoke-harness.md`
- `src/test/resources/conformance` 或 smoke fixture 目录

## 依赖

- `TASK-20260518-006` 已清理非核心 API 兼容代码。
- 真实 key 由本地环境变量或已有 credential vault 注入，不进入仓库。

## 风险

- MiMo 文档对 OpenAI SDK 与 curl header 的示例存在差异，需要支持 profile 驱动 auth strategy。
- 不同 provider 的 streaming/tool calling 响应细节可能不同，不能用 OpenAI Direct 断言硬套。
- live smoke 有成本与 rate limit 风险，默认必须 dry-run。

## 验收标准

- 默认 smoke family 不包含任何非核心 API。
- dry-run 可展示 Gemini/MiMo 每个 family 的 endpoint、method、model、auth strategy、billable/write guard。
- live smoke 必须显式开启，并在缺 key、缺预算、权限不足时输出标准 skipped/classification。
- 相关单元测试与离线 verifier 通过。

## 测试边界

- Provider-aware smoke config tests。
- Scope filtering tests。
- Credential redaction tests。
- Record/replay fixture verifier。
- 可选 live smoke 手工执行，不纳入默认 CI。

## 子任务

- [TASK-20260519-001-01 Smoke 范围矩阵与 Provider Auth 设计](TASK-20260519-001-01-smoke-scope-provider-auth-design.md)：Done
- [TASK-20260519-001-02 Gemini/MiMo Provider-aware Smoke Runner](TASK-20260519-001-02-gemini-mimo-provider-aware-smoke-runner.md)：Done
- [TASK-20260519-001-03 Record/Replay、脱敏与成本防护验证](TASK-20260519-001-03-smoke-record-replay-redaction-budget.md)：Done

## 关联文档

- [REQ-20260519-001](../../docs/requirements/REQ-20260519-001-functional-real-smoke-gemini-mimo.md)
- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)
- [TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)

## 当前状态

- 2026-05-19：父任务创建，先推进范围矩阵与 provider auth 设计。
- 2026-05-19：`TASK-20260519-001-01` 已完成，下一步进入 provider-aware smoke runner 实现。
- 2026-05-19：`TASK-20260519-001-02` 已转入实现，范围限定为 MiMo OpenAI-compatible Chat、MiMo Anthropic-compatible Messages 与 Gemini native GenerateContent/tools。
- 2026-05-19：`TASK-20260519-001-02` 已完成并归档，新增功能性 provider smoke runner；`TASK-20260519-001-03` 仍在 backlog，下一步推进 record/replay、递归脱敏与成本防护 fixture。
- 2026-05-19：根据用户补充，父任务继续强调项目核心理念不是全量 API 覆盖，而是对话、streaming、tools/function calling、多模态和直接支撑这些能力的功能性服务；`TASK-20260519-001-03` 已转入实现。
- 2026-05-19：`TASK-20260519-001-03` 已完成并归档；父任务 3 个子任务全部完成，功能性 provider smoke、certification、record/replay、脱敏与成本防护均通过全量测试。

## 实现结果

- 完成 Gemini/MiMo 功能性服务 API smoke 范围矩阵。
- 完成 provider-aware smoke runner 与 `functional-provider/smoke` 管理入口。
- 完成 functional provider certification、record/replay fixture schema、sample fixture 与离线 verifier。
- 项目核心理念已回写到 ADR、需求与文档索引：不追求全量官方 API 覆盖，聚焦对话、streaming、tools/function calling、多模态和必要支撑服务。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`：通过。
- `.\gradlew.bat test`：通过。

## 后续建议

- 后续真实 live smoke 只在本地明确提供 key、`allowLive=true` 和 `allowBillableProbes=true` 时执行。
- 后续总控继续从 `TASK-20260514-016` 和 `TASK-20260514-031` 中选择对话、tools、多模态与必要支撑能力推进。
