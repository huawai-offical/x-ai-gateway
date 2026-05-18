# TASK-20260516-017 OpenAI Direct Smoke Record/Replay Fixture 固化

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)
上游来源：[REQ-20260516-017](../../docs/requirements/REQ-20260516-017-openai-direct-smoke-record-replay-fixture.md)

## 背景

OpenAI Direct smoke certification 已有脱敏 fixture snapshots，但还缺可长期保存、可回放、可审计的 record/replay bundle。父任务 `TASK-20260514-031` 明确把 record/replay 作为剩余切片。

## 目标

- 增加 record/replay fixture response model。
- 从 certification fixture snapshots 生成稳定 bundle。
- 写入 live certification metadata。
- 增加 sample fixture 文件与 schema/脱敏测试。
- 更新 smoke harness 文档和父任务状态。

## 非目标

- 不执行真实 OpenAI 请求。
- 不自动把每次 live certification 写入仓库文件。
- 不实现 CI replay runner。

## 输入

- `OpenAiDirectSmokeCertificationService`
- `OpenAiDirectSmokeCertificationResponse`
- `OpenAiDirectSmokeCertificationFixture`
- `TASK-20260514-031` 剩余切片

## 输出

- `OpenAiDirectSmokeRecordReplayFixture`
- response/metadata 中的 `recordReplayFixture`
- sample conformance fixture JSON
- 单元测试、文档和任务回写

## 影响范围

- Admin credential smoke certification API。
- Credential metadata 安全摘要。
- Test resource fixture。
- 本地 smoke harness 文档。

## 依赖

- [TASK-20260516-007 OpenAI Direct Smoke Certification 与脱敏 Fixture 基线](../done/TASK-20260516-007-openai-direct-smoke-certification-fixture.md)
- [TASK-20260514-031 OpenAI 真实 Smoke 与认证成本防护](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)

## 风险

- 误保存 raw smoke 或原始 header 会泄露敏感信息。
- 未版本化 schema 会导致后续 replay runner 兼容成本上升。
- Dry-run 证据不能替代 live certification。

## 验收标准

- Certification API 响应包含 `recordReplayFixture.schemaVersion` 与 `fixtures`。
- Metadata 中包含 `recordReplayFixture` 且脱敏。
- Sample fixture 能被测试解析。
- 聚焦测试通过。

## 测试边界

- `OpenAiDirectSmokeCertificationServiceTests`
- `CredentialAdminServiceTests`
- `CredentialAdminControllerTests`

## 关联文档

- [REQ-20260516-017](../../docs/requirements/REQ-20260516-017-openai-direct-smoke-record-replay-fixture.md)
- [testing-smoke-harness](../../docs/testing-smoke-harness.md)
- [TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)

## 当前状态

- 2026-05-16：任务创建，进入实现。
- 2026-05-16：实现、文档、provider conformance 与聚焦回归已完成，移动到 `tasks/done/`。

## 实现结果

- 新增 `OpenAiDirectSmokeRecordReplayFixture` response model。
- Certification response 与 live metadata 均包含 `recordReplayFixture`。
- Replay policy 明确 `network=disabled_by_default`、`billableOperations=replay_only`、`writeOperations=replay_only`、`secretMaterial=redacted`。
- 新增脱敏 sample fixture 文件：`src/test/resources/conformance/openai-direct-smoke-record-replay-fixture.sample.json`。
- Provider catalog conformance 增加 `openai.direct-smoke-record-replay-fixture`。
- Smoke harness 文档、父任务与全量覆盖报告已更新。

## 验证记录

2026-05-16 已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.OpenAiDirectSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

## 遗留问题

- CI replay runner 仍需后续独立任务。
- 本任务不执行真实 OpenAI 请求，不改变真实 key/预算开关策略。
