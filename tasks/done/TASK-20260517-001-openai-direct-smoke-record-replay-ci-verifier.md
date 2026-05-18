# TASK-20260517-001 OpenAI Direct Smoke Record/Replay CI 校验器

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)
上游来源：[REQ-20260517-001](../../docs/requirements/REQ-20260517-001-openai-direct-smoke-record-replay-ci-verifier.md)

## 背景

OpenAI Direct smoke 已能生成版本化 `recordReplayFixture`，但 CI 仍缺少离线 replay fixture 校验器。为了让真实 smoke 证据具备长期回归价值，需要先把 fixture 的 schema、脱敏和 replay-only 策略变成自动化测试边界。

## 目标

- 新增可复用的 fixture verifier。
- 校验 schema version、replay mode、provider type、policy、summary 和 fixture 必填字段。
- 递归扫描敏感信息，禁止真实 key/token/org/project marker 进入 fixture。
- 增加 CI 可执行的正向和负向单元测试。
- 回写 smoke harness、父任务和 OpenAI 覆盖报告。

## 非目标

- 不访问真实 OpenAI。
- 不在本轮执行 live certification。
- 不实现完整 CLI 或 Gradle task；本轮先提供 Java verifier 与 CI 单测入口。
- 不改变真实 key vault 和成本开关策略。

## 输入

- `OpenAiDirectSmokeRecordReplayFixture`
- `OpenAiDirectSmokeCertificationFixture`
- `src/test/resources/conformance/openai-direct-smoke-record-replay-fixture.sample.json`
- `TASK-20260514-031` 剩余切片

## 输出

- `OpenAiDirectSmokeRecordReplayFixtureVerifier`
- verifier 单元测试
- 文档与任务回写
- provider catalog conformance 标记

## 影响范围

- Admin smoke certification fixture 校验逻辑。
- Conformance test resources。
- Smoke harness 文档。
- 父任务剩余切片说明。

## 依赖

- [TASK-20260516-017 OpenAI Direct Smoke Record/Replay Fixture 固化](TASK-20260516-017-openai-direct-smoke-record-replay-fixture.md)
- [TASK-20260514-031 OpenAI 真实 Smoke 与认证成本防护](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)

## 风险

- 敏感信息扫描不完整会造成 fixture 泄露。
- replay policy 检查不完整会让 CI fixture 被误用为 live 执行依据。
- 错误信息过粗会降低后续维护效率。

## 验收标准

- 仓库 sample fixture 通过 verifier。
- 故意包含 `Bearer sk-`、`AIza`、`org-real`、错误 replay policy 或缺失必要字段的 fixture 会失败。
- 聚焦测试通过。
- 需求文档、父任务、报告和索引完成回写。

## 测试边界

- `OpenAiDirectSmokeRecordReplayFixtureVerifierTests`
- 无网络、无真实 key、无数据库依赖。

## 关联文档

- [REQ-20260517-001](../../docs/requirements/REQ-20260517-001-openai-direct-smoke-record-replay-ci-verifier.md)
- [testing-smoke-harness](../../docs/testing-smoke-harness.md)
- [TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)

## 当前状态

- 2026-05-17：任务创建，进入实现。
- 2026-05-17：实现、文档、provider conformance 与聚焦测试已完成，移动到 `tasks/done/`。

## 实现结果

- 新增 `OpenAiDirectSmokeRecordReplayFixtureVerifier`，支持离线校验 record/replay fixture。
- 校验 schema version、`record_replay` 模式、`OPENAI_DIRECT` provider、ISO-8601 `recordedAt`、允许的 certification status、summary 计数和 fixture 必填字段。
- 强制 replay policy 保持 network disabled、billable/write replay-only、secret redacted 和固定 fixture source。
- 递归扫描字符串值，拦截未脱敏 `Bearer sk-`、OpenAI key、Google AI Studio key、真实 org/project marker 和常见第三方 token。
- `provider-catalog.json` 增加 `openai.direct-smoke-record-replay-ci-verifier`。
- `docs/testing-smoke-harness.md`、父任务和 OpenAI 覆盖报告已回写。

## 验证记录

2026-05-17 已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.OpenAiDirectSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

## 遗留问题

- 本任务不执行真实 OpenAI 请求。
- 真实线上 OpenAI Direct smoke 仍需受控环境提供真实 key、预算和显式开关。
