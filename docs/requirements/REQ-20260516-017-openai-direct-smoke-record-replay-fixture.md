# REQ-20260516-017 OpenAI Direct Smoke Record/Replay Fixture 固化

状态：Done
日期：2026-05-16
来源任务：[TASK-20260514-031](../../tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)

## 背景

`TASK-20260516-007` 已经能从 OpenAI Direct 资源族 smoke 生成脱敏 `fixtureSnapshots`，但这些 snapshot 只存在于接口响应和 credential metadata 中。`TASK-20260514-031` 仍要求支持 record/replay，把成功或受控跳过的真实响应脱敏后固化为可审计 fixture，供后续 CI、回归和人工比对复用。

## 目标

- 为 certification response 增加稳定的 record/replay fixture bundle。
- Bundle 只包含脱敏后的 replay 所需字段，不包含原始 secret、Organization、Project、Bearer token、真实错误正文。
- 增加仓库内 sample fixture，锁定 schema、脱敏策略和 replay policy。
- 将 live certification metadata 同步保存 `recordReplayFixture`，便于后台读取最近一次可回放证据。
- 更新 smoke harness 文档、父任务和任务索引。

## 非目标

- 不在本轮自动写入 `src/test/resources`；仓库 sample fixture 由本任务维护为静态脱敏样例。
- 不执行真实 OpenAI 请求。
- 不接入 CI 自动回放执行器；本轮只定义可回放 fixture schema 和生成器。

## 方案

1. 新增 `OpenAiDirectSmokeRecordReplayFixture` record，字段包含 `schemaVersion`、`providerType`、`baseUrl`、`certificationStatus`、`dryRun`、`recordedAt`、`summary`、`replayPolicy` 和 `fixtures`。
2. `OpenAiDirectSmokeCertificationService` 基于已脱敏的 `OpenAiDirectSmokeCertificationFixture` 构造 bundle。
3. `OpenAiDirectSmokeCertificationResponse` 增加 `recordReplayFixture` 字段。
4. `metadata` 写入 `recordReplayFixture`，但仍不写 raw `smoke`。
5. 新增 `src/test/resources/conformance/openai-direct-smoke-record-replay-fixture.sample.json` 作为脱敏 sample。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/api/`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/OpenAiDirectSmokeCertificationService.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/OpenAiDirectSmokeCertificationServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/admin/api/CredentialAdminControllerTests.java`
- `docs/testing-smoke-harness.md`
- `tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md`

## 风险

- 如果 bundle 直接复用 raw smoke，会泄露真实请求细节；实现必须只取脱敏 fixture。
- 如果 schema 不稳定，后续 CI replay 会难以维护；本轮需要版本化 `schemaVersion`。
- Dry-run fixture 不能被误判为真实认证，bundle 必须保留 `dryRun` 和 certification status。

## 验收标准

- Certification response 返回 `recordReplayFixture`。
- Live metadata 写入 `recordReplayFixture` 且不包含 `sk-`、`Bearer sk-`、`org-real`、`proj-real`。
- Sample fixture 可被 ObjectMapper 解析并包含 schema version、replay policy 和 fixtures。
- 相关 Java 单测通过。

## 测试边界

- `OpenAiDirectSmokeCertificationServiceTests`
- `CredentialAdminServiceTests`
- `CredentialAdminControllerTests`

## 实现结果

- 新增 `OpenAiDirectSmokeRecordReplayFixture`，作为 OpenAI Direct smoke certification 的版本化 record/replay bundle。
- `OpenAiDirectSmokeCertificationResponse` 增加 `recordReplayFixture`，保留 `schemaVersion`、`replayMode`、`providerType`、`baseUrl`、`certificationStatus`、`dryRun`、`recordedAt`、`summary`、`replayPolicy` 与脱敏 `fixtures`。
- `OpenAiDirectSmokeCertificationService` 只从已脱敏 `fixtureSnapshots` 构造 bundle，并在 live certification metadata 中写入 `recordReplayFixture`。
- 新增 `src/test/resources/conformance/openai-direct-smoke-record-replay-fixture.sample.json`，锁定 sample schema、脱敏字段和 replay-only 策略。
- `provider-catalog.json` 增加 `openai.direct-smoke-record-replay-fixture` conformance 标记。
- `docs/testing-smoke-harness.md` 与父任务 `TASK-20260514-031` 已同步 record/replay 边界。

## 验证记录

2026-05-16 已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.OpenAiDirectSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.CredentialAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

## 遗留问题

- CI replay runner 尚未实现；本轮只提供稳定 schema、runtime bundle 和脱敏 sample。
- 真实线上 OpenAI Direct smoke 仍需受控环境提供真实 key、预算和显式开关。
