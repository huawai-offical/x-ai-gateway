# TASK-20260519-001-03 Record/Replay、脱敏与成本防护验证

状态：Done
优先级：High
类型：子任务
父任务：[TASK-20260519-001](TASK-20260519-001-functional-real-smoke-gemini-mimo.md)
上游来源：[REQ-20260519-001](../../docs/requirements/REQ-20260519-001-functional-real-smoke-gemini-mimo.md)

## 背景

真实 Gemini/MiMo smoke 会消耗 token 并产生敏感响应，必须沿用 record/replay、脱敏和预算阻断机制，避免 CI 或默认本地测试访问真实 provider。

## 目标

- 为 Gemini/MiMo smoke 输出脱敏 fixture。
- 扩展 record/replay verifier，覆盖 provider/protocol/model/family 字段。
- 验证 budget、live allow、credential redaction 和 skipped reason。

## 非目标

- 不保存真实 key。
- 不把 live smoke 放入默认 CI。

## 输入

- Provider-aware smoke runner。
- 现有 OpenAI Direct record/replay fixture。

## 输出

- 新 fixture schema 或 schema extension。
- Verifier tests。
- 文档回写。

## 影响范围

- smoke fixture 目录
- certification service
- docs/testing-smoke-harness.md

## 依赖

- `TASK-20260519-001-02` 完成 runner。

## 风险

- 响应脱敏不足会泄露 prompt、key 或 provider 返回的敏感片段。

## 验收标准

- fixture verifier 能阻断未脱敏 key。
- 默认测试只跑 replay/offline。
- live smoke 结果能归档为脱敏 evidence。

## 测试边界

- Record/replay verifier tests。
- Redaction tests。

## 当前状态

- 2026-05-19：`TASK-20260519-001-02` 已完成，开始实现 functional-provider record/replay、递归脱敏 verifier 与成本防护 fixture。
- 2026-05-19：已新增 `FunctionalProviderSmokeCertificationService`、`FunctionalProviderSmokeRecordReplayFixtureVerifier`、功能性 provider sample fixture 与 certification endpoint；聚焦测试和全量 `.\gradlew.bat test` 通过，任务归档。

## 实现结果

- 新增 `POST /admin/credentials/{id}/functional-provider/smoke/certification`。
- 新增 `2026-05-19.functional-provider-smoke-record-replay.v1` fixture schema。
- 新增 `src/test/resources/conformance/functional-provider-smoke-record-replay-fixture.sample.json`。
- 新增 provider/protocol/model/path 范围校验，区分 Gemini GenerateContent、MiMo OpenAI-compatible Chat Completions、MiMo Anthropic-compatible Messages。
- 新增递归敏感信息扫描，覆盖 `Bearer ...`、`sk-...`、`AIza...`、`api-key=...`、`x-goog-api-key=...`、真实 org/project 和常见第三方 token。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`：通过。
- `.\gradlew.bat test`：通过。
