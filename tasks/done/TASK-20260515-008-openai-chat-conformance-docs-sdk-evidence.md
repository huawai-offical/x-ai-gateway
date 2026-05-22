# TASK-20260515-008 OpenAI Chat 参数兼容证明、公开文档与 SDK 示例

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-017](../done/TASK-20260514-017-openai-chat-completions-full-parity.md)、[TASK-20260514-029](TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 背景

`TASK-20260515-003` 到 `TASK-20260515-007` 已连续补齐 Chat create 关键参数的 DTO、canonical、native runtime 与单元测试。但公开 OpenAPI、SDK 示例和 conformance 证据仍停留在最小 smoke 或 endpoint 级别，无法让客户、管理员和后续回归快速判断哪些 Chat 参数已经 `typed/pass-through/rejected`。

## 目标

- 建立 OpenAI Chat Completions 参数级 parity/conformance 事实源，记录关键参数的状态、映射方式和证据文件。
- 更新公开 OpenAPI snapshot 与运行时 public docs OpenAPI，使 `/v1/chat/completions` 至少展示当前已强类型化和已保真的关键字段。
- 补充 JavaScript advanced SDK 示例，展示 `response_format`、`tools/tool_choice`、`store/metadata`、`web_search_options` 等参数的安全用法。
- 更新公开 Markdown 文档，明确 OpenAI Direct 与 OpenAI-compatible 站点的参数承诺边界。
- 增加测试防止 parity matrix、公开文档、OpenAPI snapshot 和 SDK 示例再次漂移。

## 非目标

- 不实现新的 Chat runtime 参数。
- 不实现完整 OpenAPI 自动生成器。
- 不承诺所有 OpenAI-compatible 第三方站点都支持 OpenAI Direct 的高级参数。
- 不执行真实 OpenAI provider smoke；真实 smoke 继续由 `TASK-20260514-031` 承接。

## 输入

- `OpenAiChatCompletionRequest`
- `OpenAiChatCompletionRequestMapper`
- `OpenAiNativeGatewayChatRuntime`
- `docs/openapi/public-openapi.json`
- `docs/sdk-examples/`
- `PublicDocsBundleService`
- conformance 资源目录

## 输出

- 参数级 conformance/parity matrix。
- 更新后的 public OpenAPI snapshot 与 runtime docs OpenAPI。
- JavaScript advanced Chat SDK 示例与索引。
- 文档说明和回归测试。

## 影响范围

- `src/test/resources/conformance/`
- `src/test/java/com/prodigalgal/xaigateway/docs/`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java`
- `docs/openapi/public-openapi.json`
- `docs/sdk-examples/`
- `docs/public-api-compatibility.md`
- `docs/public-sdk-examples.md`
- `tasks/index.md`

## 依赖

- 已完成的 `TASK-20260515-003` 至 `TASK-20260515-007`。
- `TASK-20260514-029` 后续仍需做全量事实源统一，本任务只闭环 Chat 参数证明切片。

## 风险

- 文档把 OpenAI Direct typed mapping 误写成所有兼容站点的承诺，会误导客户。
- 手写 OpenAPI 与 runtime docs 可能再次漂移，因此需要测试锁定关键字段。
- Advanced 示例如果默认启用高级参数，可能让普通 smoke 在弱兼容站点失败；示例需要显式 opt-in。

## 验收标准

- 参数 parity matrix 覆盖已实现的 Chat 关键字段，并且每个字段都有状态、映射方式和证据文件。
- public OpenAPI snapshot 与 runtime docs OpenAPI 都包含 `/v1/chat/completions` request body schema，并展示关键字段。
- SDK 示例索引包含 advanced JavaScript 示例；advanced 示例默认不破坏最小 smoke。
- 文档明确 OpenAI Direct、高级参数、OpenAI-compatible fallback 的边界。
- 对应测试通过。

## 测试边界

- 新增 docs/conformance 测试，校验 matrix、OpenAPI snapshot、SDK example、Markdown 文档一致性。
- 更新 PublicDocsBundleService tests，校验运行时 docs bundle 暴露 Chat typed 参数说明。
- 不跑真实 provider smoke。

## 实现结果

- 新增 `openai-chat-completions-parameter-parity.json` 参数级事实源，覆盖 `store`、`metadata`、penalties、`logit_bias`、`logprobs/top_logprobs`、`max_completion_tokens`、`service_tier`、`response_format`、`modalities/audio`、`web_search_options`、`tools/tool_choice`、legacy `functions/function_call` 等关键字段。
- `PublicDocsBundleService` 的 docs bundle 版本更新到 `2026.05.15`，并暴露 `chat.openai-typed-parameters` 与 `chat.openai-stored-lifecycle` conformance checks。
- runtime public OpenAPI 和 `docs/openapi/public-openapi.json` 都为 `/v1/chat/completions` 增加 request body schema，并登记 stored Chat lifecycle path。
- 新增 `docs/sdk-examples/javascript/chat-advanced-parameters.mjs`，advanced 参数默认安全展示，`web_search_options` 与 `modalities/audio` 需要显式环境变量 opt-in。
- 更新 `docs/public-api-compatibility.md` 与 `docs/public-sdk-examples.md`，明确 OpenAI Direct typed 参数能力与 OpenAI-compatible provider capability 边界。
- 新增 `OpenAiChatParameterEvidenceTests`，并扩展 public OpenAPI/docs bundle snapshot 测试，防止 parity matrix、OpenAPI、文档和 SDK 示例漂移。

## 验证结果

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.docs.OpenAiChatParameterEvidenceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"`
- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests"`
- 通过：`git diff --check -- docs/openapi/public-openapi.json docs/public-api-compatibility.md docs/public-sdk-examples.md docs/sdk-examples/index.json docs/sdk-examples/javascript/chat-advanced-parameters.mjs src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java src/test/java/com/prodigalgal/xaigateway/docs/PublicOpenApiSnapshotTests.java src/test/java/com/prodigalgal/xaigateway/docs/OpenAiChatParameterEvidenceTests.java src/test/resources/conformance/openai-chat-completions-parameter-parity.json tasks/done/TASK-20260515-008-openai-chat-conformance-docs-sdk-evidence.md`

## 遗留问题

- 本轮不跑真实 OpenAI provider smoke；真实 smoke、record/replay 和成本防护继续由 `TASK-20260514-031` 承接。
- 本轮只补 Chat 参数证明切片；全量 OpenAI coverage matrix 自动派生 public OpenAPI/catalog/docs/conformance 仍归 `TASK-20260514-029` 后续推进。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
