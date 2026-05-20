# TASK-20260519-001-01 Smoke 范围矩阵与 Provider Auth 设计

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260519-001](TASK-20260519-001-functional-real-smoke-gemini-mimo.md)
上游来源：[REQ-20260519-001](../../docs/requirements/REQ-20260519-001-functional-real-smoke-gemini-mimo.md)

## 背景

在接入 Gemini/MiMo live smoke 前，必须先固定哪些 family 能被真实 key 证明，以及每个 provider/protocol 应使用什么 base URL、path、auth strategy 和默认 guard。否则 MiMo OpenAI-compatible 可能被误当成 OpenAI Direct 全量资源族。

## 目标

- 设计 smoke scope matrix：provider、protocol、family、endpoint、model、auth strategy、默认 live 状态。
- 明确 MiMo OpenAI-compatible 与 Anthropic-compatible 的可测范围和不可测范围。
- 明确 Gemini native 的可测范围和不可测范围。
- 产出后续 runner 实现需要的配置字段和测试边界。

## 非目标

- 不实现 live runner。
- 不发起真实网络请求。
- 不修改真实 credential。

## 输入

- MiMo 官方文档来源。
- 现有 smoke harness 代码。
- `REQ-20260518-005` / `REQ-20260518-006` 的产品边界。

## 输出

- 设计记录或任务文档中的 smoke scope matrix。
- Runner 实现前置决策：auth strategy、base URL、family whitelist、skipped reason。

## 影响范围

- `docs/testing-smoke-harness.md`
- `tasks/done/TASK-20260519-001-functional-real-smoke-gemini-mimo.md`
- 后续 runner 代码设计。

## 依赖

- 当前清理任务已归档。

## 风险

- MiMo curl 示例使用 `api-key` header，而 OpenAI SDK 示例通过 SDK `api_key` 传入；设计需要避免硬编码。

## 验收标准

- 每个 provider/protocol 的 smoke family 都有明确 `SUPPORTED/UNSUPPORTED/SKIPPED` 口径。
- 非核心 API 在矩阵中显式排除。
- 后续实现可以直接按矩阵拆 runner。

## 测试边界

- 本子任务是设计任务，不运行代码测试。
- 后续实现任务补单元测试。

## 当前状态

- 2026-05-19：设计完成，已在 [docs/testing-smoke-harness.md](../../docs/testing-smoke-harness.md) 增加 Gemini/MiMo 功能性服务 API smoke 范围矩阵。

## 实现结果

- 明确 Gemini native 仅 smoke `generate_content`、`stream_generate_content`、`tool_calling`。
- 明确 MiMo OpenAI-compatible 仅 smoke Chat Completions、streaming、tools。
- 明确 MiMo Anthropic-compatible 仅 smoke Messages、streaming、tool use。
- 非核心 API 统一通过 `OUT_OF_FUNCTIONAL_API_SCOPE` 排除。
- provider auth strategy 由 site profile / smoke request 驱动，不硬编码 OpenAI Direct。

## 验证结果

- 本子任务为设计闭环，不运行代码测试。
- 设计已回写到 smoke harness 文档，并为后续 runner 实现提供验收矩阵。
