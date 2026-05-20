# TASK-20260519-001-02 Gemini/MiMo Provider-aware Smoke Runner

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260519-001](TASK-20260519-001-functional-real-smoke-gemini-mimo.md)
上游来源：[REQ-20260519-001](../../docs/requirements/REQ-20260519-001-functional-real-smoke-gemini-mimo.md)

## 背景

现有 OpenAI Direct resource smoke 偏向 OpenAI Direct 默认 resource family。下一步需要按 provider/protocol 配置执行 Gemini 与 MiMo 功能性服务 smoke。

## 目标

- 实现 provider-aware smoke runner。
- 支持 MiMo OpenAI-compatible Chat、MiMo Anthropic-compatible Messages、Gemini GenerateContent。
- 输出标准 classification 和脱敏 request preview。

## 非目标

- 不覆盖非核心官方 API。
- 不默认 live 请求。

## 输入

- `TASK-20260519-001-01` 的矩阵设计。

## 输出

- Runner 代码与单元测试。

## 影响范围

- `admin/application/*Smoke*`
- smoke API response DTO
- provider catalog smoke notes

## 依赖

- 范围矩阵设计完成。

## 风险

- provider auth header 差异可能导致真实 smoke 误失败。

## 验收标准

- dry-run 可枚举 Gemini/MiMo family。
- live guard 默认阻断。
- 单元测试覆盖 family normalization、auth strategy 与 unsupported family。

## 测试边界

- Unit tests only；live smoke 手工执行。

## 当前状态

- 2026-05-19：`TASK-20260519-001-01` 已完成，开始实现独立的功能性 provider smoke runner；默认 dry-run，live generation 必须显式开启 `allowBillableProbes`。
- 2026-05-19：已新增 `FunctionalProviderSmokeHttpClient`、`FunctionalProviderSmokeRequest/Response`、`/admin/credentials/{id}/functional-provider/smoke`，并补充 client/service 单元测试。
- 2026-05-19：已通过聚焦测试 `FunctionalProviderSmokeHttpClientTests` 与 `CredentialAdminServiceTests`，并通过全量 `.\gradlew.bat test`；任务归档。

## 实现结果

- 新增独立功能性 provider smoke runner，不复用 OpenAI Direct resource smoke，避免把 MiMo/Gemini 误归为 OpenAI Direct 全量 API。
- 支持 `GEMINI_NATIVE`、`OPENAI_COMPATIBLE`、`ANTHROPIC_COMPATIBLE` 协议推断和显式覆盖。
- 支持默认 family 矩阵和显式 family 归一化，范围外 family 返回 `UNSUPPORTED / OUT_OF_FUNCTIONAL_API_SCOPE`。
- 支持 dry-run、live guard、billable guard、标准 classification、脱敏 request preview 和最小 evidence。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`：通过。
- `.\gradlew.bat test`：通过。

## 遗留问题

- Record/replay fixture、递归脱敏 verifier 和成本防护 schema 由 `TASK-20260519-001-03` 继续承接。
