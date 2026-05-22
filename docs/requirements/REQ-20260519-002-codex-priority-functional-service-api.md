# REQ-20260519-002 Codex 功能性服务 API 最高优先推进

状态：Completed
日期：2026-05-19
上游来源：用户指令“先不做测试了，全速推进项目任务，并且把 codex 的相关任务提高到最高的优先”、[ADR-0010](../decisions/ADR-0010-functional-service-api-scope.md)、[REQ-20260518-005](REQ-20260518-005-functional-service-api-scope.md)

## 背景

项目核心理念已经从“全量覆盖 OpenAI、Anthropic、Gemini、Vertex、Codex 官方 API”收窄为“对话、streaming、tools/function calling、多模态与必要支撑能力的功能性服务 API”。在这个范围下，Codex 相关能力需要被提升为当前最高优先级，优先清理旧全量 API 口径遗留，并围绕 OpenAI 标准功能区收紧 Codex 的公开承诺、事实源、smoke 与运营入口。

本轮用户明确要求先不做测试，因此本需求只记录任务重排、边界收紧与下一步执行顺序；代码改动后的验证将在后续任务切片中补回。

## 目标

- 将 Codex 相关开放任务提升到当前最高优先级，并在 `tasks/index.md` 中形成可直接执行的 P0 队列。
- Codex 只按 OpenAI 标准功能区承接对话、streaming、tools、Responses、必要多模态、模型发现、认证、usage、审计和真实 smoke，不再追求非 Responses 的 Codex 内部 API 或官方全量 API parity。
- 优先推进 Codex 事实源统一：public OpenAPI、provider catalog、conformance、SDK 示例、门户兼容等级必须一致。
- 将 Codex smoke、record/replay、预算防护和凭证脱敏作为第二优先执行切片，沿用现有成本防护模型。
- 保留 “不跑测试” 的短期风险记录，后续每个实现切片必须补充对应验证边界。

## 非目标

- 不恢复 Fine-tuning、Batches、Evals、Admin 等非核心能力。
- 不实现 Anthropic message batches、Gemini/Vertex batch prediction、Vertex pipeline/job/admin 或非 Responses Codex 内部 API。
- 不为了历史数据或旧任务名保留兼容性口径。
- 本轮不执行自动化测试、真实 smoke 或浏览器回归。

## 范围

- 本地需求、报告与任务索引。
- Codex 相关父任务、子任务与 task spec。
- 已存在 `TASK-20260514-029`、`TASK-20260514-031`、`TASK-20260514-016` 的优先级与执行说明。

## 风险

- 短期跳过测试会让文档/任务重排缺少自动验证证据。
- 旧报告中仍可能保留“全量 API 覆盖”历史表述，需要在执行切片中逐步清理或标注为历史背景。
- Codex 既包含产品账号运营面，也包含 OpenAI-compatible API 面，后续实现需要避免把 UI/运营增强和对外 API 承诺混在一起。

## 验收标准

- `tasks/index.md` 顶部明确展示 Codex 当前最高优先队列。
- 新增 Codex 最高优先父任务与至少两个可独立执行的子任务。
- `TASK-20260514-029` 从 Medium 提升为 Critical，并明确作为 Codex 第一执行切片的上游任务。
- `TASK-20260514-031` 明确承接 Codex smoke/record-replay 复核的后续切片。
- 本轮验证口径明确记录为“按用户要求未执行测试”。

## 测试边界

本轮按用户要求不执行测试。后续进入代码实现时，子任务需要分别补齐 coverage matrix 一致性校验、provider catalog loader 校验、OpenAPI/docs bundle 校验、smoke fixture verifier 或真实 smoke 手工证据。

## 关联任务

- [TASK-20260519-002](../../tasks/done/TASK-20260519-002-codex-priority-functional-service-api.md)
- [TASK-20260519-002-01](../../tasks/done/TASK-20260519-002-01-codex-functional-truth-source-priority.md)
- [TASK-20260519-002-02](../../tasks/done/TASK-20260519-002-02-codex-smoke-record-replay-priority.md)
- [TASK-20260514-029](../../tasks/done/TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)
- [TASK-20260514-031](../../tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)
