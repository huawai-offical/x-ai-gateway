# TASK-20260519-002 Codex 功能性服务 API 最高优先推进

状态：Done
优先级：Critical
类型：父任务
上游来源：[REQ-20260519-002](../../docs/requirements/REQ-20260519-002-codex-priority-functional-service-api.md)、[REP-20260519](../../docs/reports/REP-20260519-codex-priority-replan.md)

## 背景

用户要求先不做测试、全速推进项目任务，并将 Codex 相关任务提升到最高优先。结合 [ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md)，Codex 后续只围绕 OpenAI 标准功能区提供对话、streaming、tools、Responses、多模态、模型发现、认证、usage、审计和真实 smoke，不再追求全量官方 API 或 Codex 内部 API。

## 目标

- 建立 Codex-first P0 队列。
- 将 Codex 事实源统一作为第一执行切片。
- 将 Codex smoke、record/replay 与成本防护复核作为第二执行切片。
- 后续每个切片都必须回写任务状态、实现结果和验证边界。

## 非目标

- 不恢复 Fine-tuning、Batches、Evals、Admin 等非核心 API。
- 不兼容历史全量 API 承诺。
- 本父任务不直接实现具体 controller、adapter 或前端页面。
- 本轮不执行测试。

## 输入

- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)
- [ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md)
- [TASK-20260514-029](TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)
- [TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)
- 已完成 Codex 反代、导入、观测、批量恢复和 smoke 基线任务。

## 输出

- Codex 当前最高优先任务队列。
- 两个可独立执行的 Codex 子任务。
- 更新后的 `tasks/index.md`、需求文档与重排报告。
- 后续实现切片的验证补偿边界。

## 影响范围

- `docs/index.md`
- `docs/requirements/`
- `docs/reports/`
- `tasks/index.md`
- Codex 相关 backlog 与 in-progress task spec

## 依赖

- 现有 OpenAI 标准功能区范围决策。
- 后续实现切片需要真实或 record/replay 的 Codex smoke 证据。

## 风险

- 不跑测试会延迟发现事实源链接或路径错误。
- Codex 产品运营任务和对外 API 任务容易混淆，需要在子任务中明确输出边界。

## 验收标准

- Codex 最高优先队列已写入 `tasks/index.md`。
- 至少两个 Codex 子任务存在，并说明输入、输出、风险、验收和测试边界。
- `TASK-20260514-029` 已提升为 Critical。
- 本轮跳过测试的风险已写入需求、报告和任务。

## 测试边界

本轮按用户要求不执行测试。父任务只做本地文档与任务体系重排；代码实现任务开始后再补回对应验证。

## 子任务

- [TASK-20260519-002-01 Codex 功能性服务 API 事实源优先收紧](../done/TASK-20260519-002-01-codex-functional-truth-source-priority.md)
- [TASK-20260519-002-02 Codex Smoke、Record/Replay 与成本防护复核](../done/TASK-20260519-002-02-codex-smoke-record-replay-priority.md)

## 当前状态

- 2026-05-19：父任务创建，Codex 相关任务提升为最高优先；按用户要求本轮不跑测试。
- 2026-05-19：第一切片已推进事实源收紧，新增 Codex facts 文档，并更新 public docs、OpenAPI 描述、native compatibility matrix 与测试断言；目标文件 diff check 通过，按当前测试策略归档到 Done，自动化测试待恢复后补跑。
- 2026-05-19：第二切片已实现 Codex Responses smoke record/replay fixture、sample fixture 与离线 verifier；`.\gradlew.bat compileJava compileTestJava -x test` 通过，按当前测试策略归档到 Done，自动化测试待恢复后补跑。
- 2026-05-19：父任务验收完成并归档；剩余 Codex P0 推进转入上游 `TASK-20260514-029` 与 `TASK-20260514-031`。
