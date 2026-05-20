# TASK-20260514-029-01 功能性服务 API Coverage Matrix Source

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-029](../in-progress/TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)
上游来源：[ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md)、[REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)、[REQ-20260519-002](../../docs/requirements/REQ-20260519-002-codex-priority-functional-service-api.md)

## 背景

现有 `endpoint-conformance-matrix.json` 已经提供执行证据，但它混合了 OpenAI protocol、provider native、admin、public resource 和 UI surface。`TASK-20260514-029` 需要一个更窄的事实源，只描述产品当前承诺的对话、streaming、tools、多模态与必要支撑能力，避免 OpenAPI、provider catalog、docs 和 conformance 再次漂移到全量官方 API 覆盖。

## 目标

- 新增机器可读的功能性服务 API coverage matrix source。
- 用矩阵显式区分 `core`、`supporting`、`governance` 与 `out_of_scope`。
- 覆盖 OpenAI、Anthropic、Gemini、Vertex、Codex 的当前功能性入口。
- 把 Fine-tuning、Batches、Evals、Admin、provider-specific batch/job/pipeline、非 Responses Codex 内部 API 记录为范围外。

## 非目标

- 不在本切片自动生成 public OpenAPI。
- 不重写 provider catalog loader。
- 不新增真实网络 smoke。
- 本轮按用户要求不执行测试。

## 输入

- `docs/decisions/ADR-0010-functional-service-api-scope.md`
- `src/main/resources/provider-catalog.json`
- `src/test/resources/conformance/endpoint-conformance-matrix.json`
- `src/test/resources/conformance/accepted-exceptions.json`
- `docs/codex-functional-service-api-facts.md`

## 输出

- `src/main/resources/functional-service-api-coverage-matrix.json`
- `docs/functional-service-api-coverage-matrix.md`
- `docs/index.md`、`tasks/index.md` 与父任务状态回写。

## 影响范围

- 文档事实源。
- 后续 OpenAPI、provider catalog、docs bundle、SDK examples 和 conformance consistency tests 的派生输入。

## 依赖

- `TASK-20260519-002` 已完成 Codex-first 重排。
- 后续派生与一致性测试仍归 `TASK-20260514-029` 后续切片。

## 风险

- 矩阵如果只做文字说明，会继续形成多事实源。
- 如果把 supporting 能力写成 core，后续仍可能误解为官方全量 object lifecycle。

## 验收标准

- 矩阵包含 schema version、范围、feature families、provider surfaces、derive targets 与 out-of-scope 列表。
- Codex 只出现 Responses smoke/proxy 边界。
- Anthropic、Gemini、Vertex 只按 OpenAI 标准功能区出现 Messages/GenerateContent、tools、embeddings/files 等支撑面。
- 非核心 API 以 out-of-scope 形式保留原因，不进入 supported feature families。

## 测试边界

本轮按用户要求不执行测试。非测试验证使用 JSON/Markdown diff check 与人工范围核对；恢复测试后应补 coverage matrix consistency tests。

## 当前状态

- 2026-05-19：任务创建并进入 In Progress，开始落地功能性 coverage matrix source。
- 2026-05-19：已新增 `functional-service-api-coverage-matrix.json` 与 Markdown 说明，JSON 解析通过，目标文件 `git diff --check` 通过；按当前测试策略归档，自动化一致性测试待后续切片补齐。
