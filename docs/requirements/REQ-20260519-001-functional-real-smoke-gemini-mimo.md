# REQ-20260519-001 Gemini 与 MiMo 功能性服务 API 真实 Smoke

状态：Done
日期：2026-05-19
上游来源：用户说明当前没有 OpenAI key，只有 Google Gemini key 与小米 MiMo key；MiMo key 支持 OpenAI 与 Anthropic 两种格式，需要判断能否作为真实 smoke 的替代 key，并继续按“只做对话、tools 等功能性服务 API”的产品范围推进。

## 背景

`REQ-20260518-005` 和 `REQ-20260518-006` 已将产品范围收紧到对话、streaming、tools/function calling、多模态输入输出与必要的 RAG/file_search 支撑能力。真实 smoke 也必须遵守同一边界：不能因为某个 provider 提供 OpenAI-compatible 或 Anthropic-compatible endpoint，就重新扩展到 Fine-tuning、Batches、Evals、Admin 等非核心官方 API。

项目核心理念：x-ai-gateway 不是 OpenAI、Anthropic、Gemini、Vertex、Codex 等官方 API 的全量替代层，而是面向对话、streaming、工具调用、多模态和必要支撑服务的多 provider 功能性网关。

小米 MiMo 官方文档显示：

- MiMo API Provider 支持 OpenAI-compatible 与 Anthropic-compatible API formats。
- OpenAI-compatible Base URL：`https://api.mimo-v2.com/v1`。
- Anthropic-compatible Base URL：`https://api.mimo-v2.com/anthropic`，对应请求路径示例为 `/v1/messages`。
- MiMo 文档示例覆盖 Chat Completions、streaming、function calling/tool use 与 Anthropic Messages。

因此 MiMo 可以作为“OpenAI/Anthropic 对话协议兼容 smoke key”，但不能作为 OpenAI Direct 全量 API key 的证明来源。Gemini key 则用于 Gemini/Vertex native `GenerateContent`、streaming 与 tools smoke。

## 官方来源

- MiMo welcome 文档：https://www.mimo-v2.com/docs
- MiMo FAQ：https://www.mimo-v2.com/docs/faq
- MiMo 首次调用 API：https://www.mimo-v2.com/zh/docs/quick-start/first-api-call
- MiMo 官网入口：https://mimo.mi.com/

## 目标

- 为真实 smoke 建立“功能性服务 API”范围矩阵：OpenAI-compatible Chat、Anthropic Messages、Gemini GenerateContent、streaming、tools/function calling。
- 支持用 MiMo key 作为 OpenAI-compatible Chat Completions 与 Anthropic-compatible Messages 的 smoke 凭证。
- 支持用 Gemini key 作为 Gemini native GenerateContent 与 tools smoke 凭证。
- smoke 输出必须区分 `PASS/FAIL/SKIPPED/UNSUPPORTED/NO_PERMISSION/BUDGET_BLOCKED`，并记录 provider、protocol、endpoint、model、cost guard 与 skipped reason。
- 只记录脱敏 request/response evidence；不把真实 key、原始密钥、完整敏感响应写入仓库。

## 非目标

- 不用 MiMo key 覆盖 OpenAI `/v1/responses`、Files、Uploads、Vector Stores、Realtime client secret 等未由 MiMo 官方文档确认的 OpenAI Direct 专属 API。
- 不 smoke Fine-tuning、OpenAI `/v1/batches`、Anthropic Message Batches、Gemini/Vertex batch prediction、Evals、Admin、provider pipeline/job/admin。
- 不把真实 key 写入代码、配置样例或测试 fixture。
- 不默认执行高成本、多轮、长上下文或写入型 smoke。

## 范围

- `TASK-20260514-031` 真实 smoke harness。
- OpenAI-compatible provider smoke：Chat Completions、streaming、tools/function calling。
- Anthropic-compatible provider smoke：Messages、streaming、tool use。
- Gemini native smoke：GenerateContent、streaming、function calling。
- Admin/CLI smoke 入口、record/replay、脱敏与成本防护。

## 风险

- MiMo OpenAI-compatible 文档示例中 Python SDK 使用 `api_key`，curl 示例使用 `api-key` header；实现需要让 auth strategy 由 provider site profile 决定，不能硬编码为 OpenAI Direct `Authorization: Bearer`。
- MiMo 兼容 OpenAI/Anthropic 不等于支持 OpenAI Direct 全量资源族；smoke 分类必须避免误报。
- 真实 Gemini/MiMo 请求可能产生成本或触发 rate limit，需要默认 dry-run 或显式 allow-live。
- streaming 与 tool calling 的响应形态可能和 OpenAI/Anthropic 官方存在细节差异，需要 record/replay 和 conformance fixture 区分 provider。

## 验收标准

- 新增或更新 smoke 配置模型，能声明 provider、protocol、base URL、auth strategy、model 与 smoke family。
- MiMo OpenAI-compatible smoke 只覆盖 Chat Completions 及 tools/streaming；非核心 family 返回 `UNSUPPORTED` 或不进入默认列表。
- MiMo Anthropic-compatible smoke 只覆盖 Messages 及 tool use/streaming；Message Batches 不进入默认列表。
- Gemini native smoke 覆盖 GenerateContent 基线，并明确不覆盖 batch prediction。
- 所有真实 smoke 默认不执行 live 请求；只有显式开关、凭证引用和成本预算同时满足时才执行。
- 单元测试与 record/replay fixture 验证脱敏、分类、范围过滤和 provider-specific auth。

## 测试边界

- Harness unit tests：family normalization、scope filtering、provider auth strategy、dry-run classification。
- Record/replay verifier：脱敏与 schema。
- Live smoke：仅在本地显式注入 Gemini/MiMo key 与 allow-live flag 时执行；仓库默认测试不访问真实 provider。

## 实现记录

- 2026-05-19：新增 `functional-provider/smoke` 管理端入口与独立 runner，支持 `GEMINI_NATIVE`、`OPENAI_COMPATIBLE`、`ANTHROPIC_COMPATIBLE` 三类协议。
- 2026-05-19：默认 family 已限定为 Gemini `generate_content/stream_generate_content/tool_calling`、MiMo OpenAI-compatible `chat_completions/chat_streaming/chat_tools`、MiMo Anthropic-compatible `messages/messages_streaming/tool_use`。
- 2026-05-19：live 请求需要 `dryRun=false` 且 `allowLive=true`；billable generation 还需要 `allowBillableProbes=true`，否则返回 `BUDGET_BLOCKED / BILLABLE_PROBE_BLOCKED`。
- 2026-05-19：OpenAI Direct credential 不进入该 runner，返回 `UNSUPPORTED / PROVIDER_NOT_FUNCTIONAL_SMOKE_COMPATIBLE`，避免把 MiMo/Gemini smoke 与 OpenAI Direct 全量资源族混淆。
- 2026-05-19：已补单元测试覆盖 dry-run、live guard、范围外 family、MiMo `api-key`、Anthropic-compatible `anthropic-version`、Gemini `x-goog-api-key` 与脱敏。
- 2026-05-19：`TASK-20260519-001-03` 已完成；record/replay fixture、递归脱敏 verifier 和成本防护 schema 已闭环，并通过全量 `.\gradlew.bat test`。

## 关联文档

- [REQ-20260518-005 对话与 Tools 功能性服务 API 范围收窄](REQ-20260518-005-functional-service-api-scope.md)
- [REQ-20260518-006 非核心 API 兼容代码彻底清理](REQ-20260518-006-non-core-api-code-eradication.md)
- [ADR-0010 对话与 Tools 功能性服务 API 作为产品范围](../decisions/ADR-0010-functional-service-api-scope.md)
- [TASK-20260519-001 Gemini 与 MiMo 功能性服务 API 真实 Smoke](../../tasks/done/TASK-20260519-001-functional-real-smoke-gemini-mimo.md)

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeHttpClientTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`：通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeCertificationServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.FunctionalProviderSmokeRecordReplayFixtureVerifierTests" --tests "com.prodigalgal.xaigateway.admin.application.CredentialAdminServiceTests"`：通过。
- `.\gradlew.bat test`：通过。

## 完成结论

Gemini/MiMo 真实 smoke 已按功能性服务 API 范围完成：MiMo 只作为 OpenAI-compatible Chat 与 Anthropic-compatible Messages 的对话协议 smoke key，Gemini 只作为 GenerateContent/tools smoke key；默认不执行 live，不覆盖 Fine-tuning、Batches、Evals、Admin 等非核心官方 API。record/replay、脱敏、成本防护和离线 verifier 已闭环。
