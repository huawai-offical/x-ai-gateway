# TASK-20260516-010 OpenAI Provider Catalog 覆盖边界校准

状态：Done
优先级：High
类型：子任务切片
父任务：[TASK-20260514-015](../backlog/TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md)
关联任务：[TASK-20260514-029](TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)、[TASK-20260514-016](../backlog/TASK-20260514-016-functional-service-api-coverage-parent.md)

## 背景

`TASK-20260514-015` 指出 public OpenAPI、provider catalog 和 conformance accepted exceptions 存在事实源漂移。当前 `provider-catalog.json` 中 OpenAI preset 的 `unsupportedFeatures` 仍为空，但 backlog 与审计报告已经明确仍缺 Conversations、Vector Stores 全栈、Fine-tuning events/checkpoints、Administration、Realtime calls/WebRTC/SIP 等完整官方资源族能力。空数组会误导客户把当前 OpenAI Direct 视为官方 API 全量覆盖。

## 目标

- 校准 OpenAI preset 的 `conformanceChecks`，体现已闭环的 Chat/Responses/Realtime/smoke 证据。
- 将 OpenAI Direct 未完成或部分完成的官方资源族写入 `unsupportedFeatures`。
- 在公开 docs bundle 测试中锁定 OpenAI preset 不得再出现空 `unsupportedFeatures`。
- 在公开兼容文档中说明 OpenAI Direct native-first 与 OpenAI 官方全量 API 的边界。

## 非目标

- 不实现缺失资源族。
- 不修改路由或运行时行为。
- 不生成完整 coverage matrix 文件。

## 输入

- `src/main/resources/provider-catalog.json`
- `docs/public-api-compatibility.md`
- `ProviderCatalogLoaderTests`
- `PublicDocsBundleServiceTests`

## 输出

- OpenAI preset `unsupportedFeatures` 非空且与本切片闭环时的 backlog 一致。
- OpenAI preset `conformanceChecks` 包含近期已闭环能力。
- public docs bundle 与 provider catalog loader 测试覆盖 OpenAI 边界。

## 影响范围

- `src/main/resources/provider-catalog.json`
- `docs/public-api-compatibility.md`
- `src/test/java/com/prodigalgal/xaigateway/admin/application/ProviderCatalogLoaderTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java`
- `tasks/backlog/TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md`
- `docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md`

## 依赖

- 已完成的 Chat/Responses/OpenAI Direct smoke/Reatime WebSocket 切片。
- `accepted-exceptions.json` 在本任务闭环时仍记录 `GET /v1/batches`、fine-tuning events/checkpoints 等缺口；其中 `/v1/batches [GET]` 后续已由 `TASK-20260516-011` 回收。

## 风险

- `unsupportedFeatures` 写得过宽会低估已实现能力；写得过窄会继续误导客户。
- conformance checks 如果写成未来能力，会把未实现路径误标为已验收。

## 验收标准

- OpenAI preset `unsupportedFeatures` 不为空，并至少覆盖 Conversations、Vector Stores full stack、Fine-tuning events/checkpoints、Administration、Realtime full calls/WebRTC/SIP、Models delete 等本切片闭环时的缺口；Batch list 后续已由 `TASK-20260516-011` 移出未完成边界，Models delete 的 gateway registry 边界后续已由 `TASK-20260516-012` 闭环。
- OpenAI preset `conformanceChecks` 包含 Chat typed parameters、Responses native create/stream/lifecycle、Realtime WebSocket ingress 和 OpenAI Direct smoke certification。
- Provider catalog loader 测试和 public docs bundle 测试都锁定上述边界。
- Targeted tests 和 scoped `diff --check` 通过。

## 测试边界

- 只测 catalog/docs 事实源，不做真实 provider 网络访问。
- 不校验每个 OpenAI 官方 endpoint 的完整实现，只校验本切片声明边界。

## 当前状态

- 已完成 catalog、docs、父任务、报告和测试回写。

## 实现结果

- OpenAI preset 的 `conformanceChecks` 已加入 Chat typed parameters、stored chat DB cursor pagination、Responses native JSON/SSE/lifecycle、Realtime WebSocket ingress、OpenAI Direct smoke certification 与 explicit billable/write smoke。
- OpenAI preset 的 `unsupportedFeatures` 已从空数组改为明确列出 Conversations、Vector Stores full stack、Fine-tuning events/checkpoints/pause/resume、Models delete、Containers/Code Interpreter、Evals/Graders/Runs、Administration API、Realtime full calls/WebRTC/SIP/translation/transcription 等未完成边界；Batch list 后续已由 `TASK-20260516-011` 闭环。
- `docs/public-api-compatibility.md` 已说明 OpenAI Direct 是 `openai-native` / `native-first`，但不等于官方 API 全量覆盖。
- `ProviderCatalogLoaderTests` 与 `PublicDocsBundleServiceTests` 已锁定 OpenAI preset 的非空 unsupportedFeatures 和公开 docs bundle 传播。

## 验证记录

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"
```

结果：通过。

## 遗留与后续

- 本切片只修 provider catalog/docs 边界，不生成完整 OpenAI coverage matrix。
- public OpenAPI 与 accepted exceptions 的自动派生仍需后续 `TASK-20260514-029` 切片继续推进。
