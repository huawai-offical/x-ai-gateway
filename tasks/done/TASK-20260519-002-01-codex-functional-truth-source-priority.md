# TASK-20260519-002-01 Codex 功能性服务 API 事实源优先收紧

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260519-002](TASK-20260519-002-codex-priority-functional-service-api.md)
上游来源：[REQ-20260519-002](../../docs/requirements/REQ-20260519-002-codex-priority-functional-service-api.md)、[TASK-20260514-029](TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)

## 背景

`TASK-20260514-029` 原本是 OpenAPI、Catalog、Conformance 与 SDK 事实源统一任务，优先级为 Medium。当前用户已将 Codex 相关任务提升到最高优先，因此本子任务先从 Codex 维度推进事实源收紧，确保 Codex 对外只声明对话、streaming、tools、Responses、多模态与必要支撑能力。

## 目标

- 梳理 Codex 相关 public docs、OpenAPI、provider catalog、conformance accepted exceptions、SDK 示例和门户兼容等级。
- 删除或标记所有暗示 Codex 全量 API、非 Responses 内部 API、Fine-tuning、Batches、Evals、Admin parity 的公开承诺。
- 将 Codex 能力统一映射到 OpenAI 标准功能区。
- 形成后续可测试的 coverage matrix/checklist。

## 非目标

- 不实现新的 Codex controller 或 adapter。
- 不处理非 Codex provider 的事实源收紧，除非它们直接影响 Codex/OpenAI 标准功能区。
- 本轮不执行自动化测试。

## 输入

- `docs/openapi/public-openapi.json`
- `src/main/resources/provider-catalog.json`
- `docs/public-api-compatibility.md`
- `docs/public-sdk-examples.md`
- Codex 相关任务与报告。

## 输出

- Codex-first facts checklist。
- 需要修改的文件清单与执行结果。
- `TASK-20260514-029` 的 Codex 切片进展回写。

## 影响范围

- docs、provider catalog、public OpenAPI、SDK 示例、任务文档。

## 依赖

- [ADR-0010](../../docs/decisions/ADR-0010-functional-service-api-scope.md)
- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)

## 风险

- 事实源分散在 docs、catalog、fixtures 和前端展示中，手工清理容易遗漏。
- 旧历史报告可以保留历史背景，但不能作为当前支持承诺。

## 验收标准

- Codex 公开支持面只围绕功能性服务 API。
- 不再出现 Codex 非 Responses 内部 API 或非核心 API parity 的当前承诺。
- `TASK-20260514-029` 已标记 Codex 切片优先级提升和执行边界。
- Public docs bundle、OpenAPI 描述与 native compatibility matrix 明确 Codex 不进入通用 provider catalog preset，只保留 Responses smoke/反代边界。

## 测试边界

本轮按用户要求不执行测试。后续实现后应补充 docs bundle、OpenAPI/catalog consistency 或人工 diff 检查。

## 关联任务

- [TASK-20260514-029](TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md)
- [TASK-20260514-016](../backlog/TASK-20260514-016-functional-service-api-coverage-parent.md)

## 当前状态

- 2026-05-19：已作为 Codex P0 第一执行切片创建。
- 2026-05-19：新增 [Codex 功能性服务 API 事实源](../../docs/codex-functional-service-api-facts.md)，并收紧 public docs bundle、OpenAPI 描述、native compatibility matrix 与测试断言中的 Codex 边界；目标文件 `git diff --check` 通过；按用户要求未执行测试。
- 2026-05-19：按当前“先不跑测试、全速推进”的策略归档；验证证据为目标文件 `git diff --check` 与 `.\gradlew.bat compileJava compileTestJava -x test`，自动化测试待用户恢复测试后补跑。
