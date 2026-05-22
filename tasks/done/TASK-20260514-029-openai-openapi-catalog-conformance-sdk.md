# TASK-20260514-029 对话与 Tools OpenAPI、Catalog、Conformance 与 SDK 事实源统一

状态：Completed
优先级：Critical
类型：子任务
父任务：[TASK-20260514-016](../backlog/TASK-20260514-016-functional-service-api-coverage-parent.md)
上游来源：[TASK-20260514-015](../done/TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md)、[TASK-20260519-002](../done/TASK-20260519-002-codex-priority-functional-service-api.md)

## 背景

当前 public OpenAPI、provider catalog、conformance accepted exceptions 和实际 controller 覆盖面不一致。2026-05-18 后不再追求 OpenAI 或其它 provider 官方全量 API 覆盖，事实源需要收敛到跨 Anthropic、Gemini、Vertex、Codex 的 OpenAI 标准功能区。

2026-05-19 用户要求 Codex 相关任务提升为最高优先。本任务优先级从 Medium 提升为 Critical，并先按 [TASK-20260519-002-01](../done/TASK-20260519-002-01-codex-functional-truth-source-priority.md) 执行 Codex 切片；该切片已按当前测试策略归档，自动化测试待用户恢复测试后补跑。2026-05-21 再次复核后，Codex 与 UI/UX 相关任务继续保持最高优先级，当前执行项收敛到 OpenAPI 路径补全与 SDK 三模式示例归口。

## 目标

- 建立对话/tools endpoint coverage matrix 的机器可读文件。
- 从 coverage matrix 派生 public OpenAPI、provider catalog unsupportedFeatures、docs bundle 和 conformance fixtures。
- 更新 SDK examples，区分 OpenAI Direct native、OpenAI-compatible Generic 和本项目自定义 API。
- 在门户展示兼容等级，不再只写“OpenAI-compatible”或暗示官方全量 API parity。
- 明确 Anthropic、Gemini、Vertex、Codex 只保留可映射到 OpenAI 标准功能区的 provider/native surface。

## 非目标

- 不直接实现缺失 API。
- 不用文档伪装已实现能力。

## 输入

- `docs/openapi/public-openapi.json`
- `src/main/resources/provider-catalog.json`
- conformance fixtures 和 accepted exceptions。
- 所有 OpenAI 子任务状态。

## 输出

- Conversation/tools coverage matrix source。
- 自动或半自动生成的 OpenAPI/catalog/docs/conformance 更新。
- SDK examples 与客户文档。

## 影响范围

- docs、provider catalog、public API bundle、portal docs、tests。

## 依赖

- `TASK-20260514-030` 横切协议字段。
- 各子任务完成状态。

## 风险

- 手写多份事实源会再次漂移。
- 公开未稳定 API 会形成错误兼容承诺。

## 验收标准

- public OpenAPI 与 controller 覆盖差异可解释。
- Catalog unsupportedFeatures 不为空且与 coverage matrix 一致。
- Conformance accepted exceptions 都有关联任务或 out-of-scope 决策。

## 测试边界

- 本轮按用户指令不运行测试。
- 已执行 JSON 解析、OpenAPI 目标路径比对、Gradle 强类型编译和目标文件 `git diff --check`。
- 后续恢复测试时补跑 Docs bundle tests、Provider catalog loader tests 与 Coverage matrix consistency tests。

## 已完成切片

- [TASK-20260515-008 OpenAI Chat 参数兼容证明、公开文档与 SDK 示例](../done/TASK-20260515-008-openai-chat-conformance-docs-sdk-evidence.md)：先完成 Chat 参数证明切片，新增参数级 parity matrix、public OpenAPI Chat request schema、runtime docs bundle typed parameter 说明、JavaScript advanced 示例 and 漂移防护测试。
- [TASK-20260516-010 OpenAI Provider Catalog 覆盖边界校准](../done/TASK-20260516-010-openai-provider-catalog-coverage-boundary.md)：OpenAI Direct provider catalog 现在公开 native-first 支持面与未完成官方资源族边界，避免客户把当前 gateway 误认为官方 API 全量覆盖。
- **Codex 深度融合与 Session 恢复切片 (TASK-20260514-029-02, -03)**：完成了 Codex Responses 私有端点规范同步与不支持特性限定、~/.codex/config.toml CLI 接入最佳实践指南、一键生成/复制 CLI 恢复指令以及账号运行态的 Responses boundary 属性、健康路由权重负载、Dry-run/Record/Replay 脱敏参数前端可视化面板，已成功通过 Gradlew 强类型编译与 Bun TS 类型检查。

## 剩余切片

- 无。OpenAPI 路径、SDK 示例和 coverage matrix 派生状态已在 `TASK-20260514-029-04` 收尾；Fine-tuning、Batches、Evals、Administration、Anthropic message batches、Gemini/Vertex batch prediction、Vertex pipeline/job/admin、非 Responses Codex 内部 API 等非核心公开入口保持 out-of-scope。

## 子任务

- [TASK-20260514-029-01 功能性服务 API Coverage Matrix Source](../done/TASK-20260514-029-01-functional-service-api-coverage-matrix-source.md)
- [TASK-20260514-029-02 Codex OpenAPI, Catalog & Conformance 深度融合](../done/TASK-20260514-029-02-codex-openapi-catalog-conformance.md)
- [TASK-20260514-029-03 Codex 运营控制台体验对标与 Session 恢复桥接](../done/TASK-20260514-029-03-codex-console-session-recovery.md)
- [TASK-20260514-029-04 OpenAPI 路径补全与 SDK 三模式示例归口](TASK-20260514-029-04-openapi-coverage-sdk-finalization.md)

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REQ-20260518-005](../../docs/requirements/REQ-20260518-005-functional-service-api-scope.md)
- [REQ-20260519-002](../../docs/requirements/REQ-20260519-002-codex-priority-functional-service-api.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
- [REP-20260519 Codex 最高优先级重排](../../docs/reports/REP-20260519-codex-priority-replan.md)
- [REP-20260521 项目进度复核与 Codex/UI/UX 优先级重排](../../docs/reports/REP-20260521-project-progress-codex-uiux-reprioritization.md)

## 当前状态

- 2026-05-19：从 Backlog 转入 In Progress，第一执行切片 [TASK-20260514-029-01](../done/TASK-20260514-029-01-functional-service-api-coverage-matrix-source.md) 已建立功能性 coverage matrix source 并归档；后续继续收敛 OpenAPI、catalog、docs、SDK 与 conformance。
- 2026-05-20：Codex 专属切片（TASK-20260514-029-02 与 TASK-20260514-029-03）完全开发完毕，已通过编译与 TS 类型静态检查，执行归档，将子任务物理移动至 done 目录。
- 2026-05-21：按项目进度变化重新审视任务队列，确认 `TASK-20260514-029-04` 为当前 P0-CODEX 最高优先执行项，UI/UX 专项成果继续作为后续界面变更的验收基线。
- 2026-05-21：`TASK-20260514-029-04` 已完成并归档，父任务随之移动至 `tasks/done/`。

## 验证结果

- `docs/openapi/public-openapi.json` 可被 `ConvertFrom-Json` 解析。
- `src/main/resources/functional-service-api-coverage-matrix.json` 可被 `ConvertFrom-Json` 解析。
- `docs/openapi/public-openapi.json` 已包含 `TASK-20260514-029-04` 要求的 15 个目标路径。
- `.\gradlew.bat compileJava -x test` 通过。
- 目标文件 `git diff --check` 通过。
