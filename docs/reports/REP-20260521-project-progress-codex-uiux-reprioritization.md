# REP-20260521 项目进度复核与 Codex/UI/UX 优先级重排

状态：Completed
日期：2026-05-21
关联需求：[REQ-20260518-005](../requirements/REQ-20260518-005-functional-service-api-scope.md)、[REQ-20260519-002](../requirements/REQ-20260519-002-codex-priority-functional-service-api.md)、[REQ-20260520-001](../requirements/REQ-20260520-001-ui-ux-console-portal-experience.md)

## 背景

项目范围已从“官方 API 全量覆盖”收紧为对话、streaming、tools/function calling、多模态输入输出，以及必要的认证、模型发现、RAG/file_search、审计、usage 和 smoke 支撑能力。2026-05-21 用户要求重新审视当前代码进度和任务变化，并继续提高 Codex、UI/UX 相关任务优先级。

## 当前结论

- Codex 相关任务继续保持最高优先级，执行边界只覆盖功能性服务 API，不恢复 Fine-tuning、Batches、Evals、Admin 或非 Responses Codex 内部 API。
- UI/UX 专项已完成一轮归档，但仍保持 P0 可见，用于约束后续 Codex、Portal、Admin Console 的交互质量。
- 当前 P0 执行项 [TASK-20260514-029-04](../../tasks/done/TASK-20260514-029-04-openapi-coverage-sdk-finalization.md) 已完成：OpenAPI 路径补全、SDK 三模式示例和 coverage matrix 派生状态已回写。
- [TASK-20260514-031](../../tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md) 仍保留为真实 smoke 与认证成本防护任务；按用户当前指令暂不执行测试，因此暂不进入真实 key 执行。

## 进度复核

| 方向 | 当前状态 | 下一步 |
| --- | --- | --- |
| Codex facts / OpenAPI / SDK | `TASK-20260514-029-01/02/03/04` 已归档 | 后续只在 coverage matrix 新增能力时继续派生 |
| Codex console / session recovery | 已归档 | 后续只在功能性 API 主线产生 UI 变更时继续硬化 |
| UI/UX console / portal | `TASK-20260520-001` 已归档 | 保持 P0 可见，不新增泛化重做；只承接具体交互缺口 |
| 多模态支撑参数 | `TASK-20260514-020` Done | 已完成 Audio translations 与 Images edits/variations 清理、transcription 参数补充和 OpenAPI 派生 |
| Files/Uploads/Models 支撑面 | `TASK-20260514-021` Done | 已完成 Files list 参数/envelope，并确认 Batches/Fine-tuning 不回到公开支持面 |
| 真实 smoke | `TASK-20260514-031` Backlog | 用户恢复测试或提供真实 key 后再执行 |

## 当前 P0 执行顺序

1. P0-CODEX-03c：[TASK-20260514-029-04](../../tasks/done/TASK-20260514-029-04-openapi-coverage-sdk-finalization.md) OpenAPI 路径补全与 SDK 三模式示例归口，已完成。
2. P0-FUNC-01：[TASK-20260514-020](../../tasks/done/TASK-20260514-020-openai-multimodal-supporting-parameters.md) OpenAI 多模态支撑参数边界收紧，已归档。
3. P0-FUNC-02：[TASK-20260514-021](../../tasks/done/TASK-20260514-021-openai-files-uploads-models-functional-support.md) OpenAI Files、Uploads、Models 功能性支撑面，已归档。
4. P0-CODEX-05：[TASK-20260514-031](../../tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md) OpenAI / OpenAI-compatible 真实 smoke、认证和成本防护，等待恢复测试或真实 key 执行窗口。
5. P0-UI-UX-01：[TASK-20260520-001](../../tasks/done/TASK-20260520-001-ui-ux-console-portal-experience.md) 已归档，作为后续界面变更验收基线。

## 本轮验证策略

用户已要求先不做测试，因此本轮只执行非测试验证：

- OpenAPI JSON 可解析。
- coverage matrix JSON 可解析。
- 目标文件 `git diff --check`。
- 修改 Java 代码后执行 `.\gradlew.bat compileJava -x test`；涉及测试源码引用清理时执行 `.\gradlew.bat compileTestJava -x test`，仅做强类型编译，不运行测试。

## 风险

- 公开 OpenAPI 过度声明会误导客户认为网关支持官方 API 全量 parity；新增路径必须带有功能性服务边界说明。
- UI/UX 工作区存在大量既有未提交文件，本轮不回滚、不重排这些无关变更，只在任务索引中反映已归档状态。
- Smoke 与真实 provider key 相关工作暂缓，后续恢复测试时需单独补证据。
