# REQ-20260517-001 OpenAI Direct Smoke Record/Replay CI 校验器

状态：Done
日期：2026-05-17
来源任务：[TASK-20260514-031](../../tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)

## 背景

`TASK-20260516-017` 已经把 OpenAI Direct smoke certification 固化为版本化 `recordReplayFixture`，并提供仓库内脱敏 sample。父任务 `TASK-20260514-031` 仍要求 record/replay 能进入 CI 或受控回归流程，否则 fixture 只能被人工查看，不能稳定证明 schema、脱敏和 replay-only 策略没有退化。

## 目标

- 增加离线 CI replay verifier，默认只读取本地 fixture，不访问真实 OpenAI。
- 校验 `recordReplayFixture` 的 schema version、replay mode、provider type、summary、policy 与 fixtures 完整性。
- 校验 replay policy 必须保持 `network=disabled_by_default`、billable/write 均为 `replay_only`。
- 校验 fixture 内容不包含常见 secret、Bearer token、OpenAI key、真实 org/project marker。
- 把 verifier 接入单元测试，作为 CI 可执行的无外部依赖回归。
- 更新 smoke harness 文档、父任务和任务索引。

## 非目标

- 不执行真实 OpenAI 请求。
- 不生成新的 live certification。
- 不把用户真实 key、真实 org/project 或真实响应写入仓库。
- 不实现对外 CLI；本轮先提供服务级 verifier 和测试入口。

## 方案

1. 新增 `OpenAiDirectSmokeRecordReplayFixtureVerifier`，接收 `JsonNode` 或 classpath fixture 路径，输出结构化校验报告。
2. verifier 递归扫描 fixture 文本，拦截 `sk-`、`Bearer sk-`、`AIza`、`org-real`、`proj-real` 等敏感形态。
3. verifier 对每个 fixture 校验 `resourceFamily`、`classification`、`method`、`path`、`requestPreview` 与安全 evidence 边界。
4. 增加 sample fixture 正向测试和故障 fixture 负向测试。
5. 文档中明确 CI 命令、默认离线、失败条件和未来可接入受控 live fixture 的边界。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/admin/application/`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/`
- `src/test/resources/conformance/openai-direct-smoke-record-replay-fixture.sample.json`
- `docs/testing-smoke-harness.md`
- `tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md`
- `docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md`
- `tasks/index.md`

## 风险

- verifier 过松会让含密 fixture 进入 CI。
- verifier 过严会阻断合法的脱敏 evidence；需要给出清晰失败原因。
- 如果只检查顶层字段，resource fixture 仍可能缺少 request/evidence 边界。

## 验收标准

- sample fixture 通过离线 verifier。
- 含敏感 token、错误 replay policy、缺失 fixture 字段的样例会失败并返回可读错误。
- 聚焦测试可在无网络、无真实 key 环境通过。
- 文档和父任务记录 CI replay verifier 已闭环，剩余真实 live smoke 仍保留受控开关边界。

## 测试边界

- `OpenAiDirectSmokeRecordReplayFixtureVerifierTests`
- 不访问网络，不依赖真实 OpenAI key。

## 实现结果

- 新增 `OpenAiDirectSmokeRecordReplayFixtureVerifier`，对 `recordReplayFixture` 执行离线 schema、summary、policy 与 fixture 必填字段校验。
- 校验器递归扫描字符串值，拦截 `Bearer sk-`、`sk-`、`AIza`、真实 `org-*`、真实 `proj-*` 以及常见第三方 token 形态。
- 校验 `replayPolicy.network=disabled_by_default`、`billableOperations=replay_only`、`writeOperations=replay_only`、`secretMaterial=redacted` 和固定 `fixtureSource`。
- 新增 `OpenAiDirectSmokeRecordReplayFixtureVerifierTests`，覆盖仓库 sample 正向、错误 replay policy、未脱敏 secret、缺失 fixture 必填字段和 summary 计数不一致。
- `provider-catalog.json` 增加 `openai.direct-smoke-record-replay-ci-verifier` conformance 标记。
- Smoke harness 文档、父任务与 OpenAI 覆盖报告已同步。

## 验证记录

2026-05-17 已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.OpenAiDirectSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

## 遗留问题

- 本任务只实现离线 fixture verifier，不执行真实 OpenAI live smoke。
- 真实线上 OpenAI Direct smoke 仍需要受控环境提供真实 key、预算和显式开关。
