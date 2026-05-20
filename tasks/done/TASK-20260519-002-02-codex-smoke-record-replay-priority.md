# TASK-20260519-002-02 Codex Smoke、Record/Replay 与成本防护复核

状态：Done  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260519-002](TASK-20260519-002-codex-priority-functional-service-api.md)  
上游来源：[REQ-20260519-002](../../docs/requirements/REQ-20260519-002-codex-priority-functional-service-api.md)、[TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)

## 背景

Codex 已有真实 smoke 分类与预算阻断基线，但在功能性服务 API 范围收窄后，需要重新确认 smoke、record/replay、脱敏和成本防护只覆盖对话、Responses、streaming、tools 与必要支撑能力。

## 目标

- 复核 Codex smoke 分类是否仍符合 `PASS/FAIL/SKIPPED/UNSUPPORTED/NO_PERMISSION/BUDGET_BLOCKED`。
- 确认默认 dry-run、record/replay 和预算阻断不会触发高成本或写操作。
- 清理 smoke 文档中任何非核心 API 覆盖暗示。
- 明确没有 OpenAI key 时如何用可替代 provider 的 record/replay 或 compatible key 做功能性验证。

## 非目标

- 不默认执行真实线上 smoke。
- 不引入高成本写操作。
- 不扩展 Codex 非 Responses 内部 API。

## 输入

- [TASK-20260514-031](../backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md)
- [TASK-20260515-016](../done/TASK-20260515-016-openai-codex-real-smoke-classification-budget-guard.md)
- [TASK-20260519-001](../done/TASK-20260519-001-functional-real-smoke-gemini-mimo.md)
- `docs/testing-smoke-harness.md`

## 输出

- Codex smoke/record-replay 复核结果。
- 必要的文档或 fixture 更新。
- 后续真实 smoke 执行条件与成本开关说明。

## 影响范围

- Smoke harness、certification fixture、docs、credential metadata、安全审计。

## 依赖

- 可用的 Codex/OpenAI-compatible 凭证或脱敏 record/replay fixture。
- [TASK-20260519-002-01](../done/TASK-20260519-002-01-codex-functional-truth-source-priority.md) 产出的事实源边界。

## 风险

- 无真实 OpenAI key 时不能把未执行的线上 smoke 误写成通过。
- 真实响应和 auth 信息必须保持脱敏。

## 验收标准

- Codex smoke 目标只覆盖功能性服务 API。
- 默认路径不触发真实网络或成本。
- 所有 skipped/budget/permission 分类可解释。
- `codexResponsesSmoke` 返回和账号 `lastRefreshResultJson` 包含版本化 `recordReplayFixture`。
- 仓库存在脱敏 sample fixture 和离线 verifier，且 verifier 不访问真实网络。

## 测试边界

本轮按用户要求不执行测试。已执行非测试验证：`.\gradlew.bat compileJava compileTestJava -x test`。

待用户恢复测试后，优先运行：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.OfficialAccountAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.CodexResponsesSmokeRecordReplayFixtureVerifierTests"
```

真实 Codex/OpenAI 网络 smoke 不纳入默认验证；只有显式 `dryRun=false` 且路由、凭证、预算开关均允许时才执行。

## 当前状态

- 2026-05-19：已加入 Codex P0 队列。第一切片已明确 `codex.responses-smoke-boundary`，后续应在不默认访问真实网络的前提下复核 dry-run preview、usage budget guard、record/replay fixture 和脱敏输出。
- 2026-05-19：转入 In Progress，开始实现 Codex Responses smoke record/replay 脱敏 fixture 输出；按用户要求暂不运行测试。
- 2026-05-19：已实现 `OfficialCodexResponsesSmokeResponse.recordReplayFixture`、last refresh metadata 写入、脱敏 sample fixture 与 `CodexResponsesSmokeRecordReplayFixtureVerifier`；`.\gradlew.bat compileJava compileTestJava -x test` 通过；测试断言已补充但按用户要求未运行。
- 2026-05-19：修正从 backlog 移入 in-progress 后的相对链接，并补充恢复测试后的目标命令；按当前“先不跑测试、全速推进”的策略归档，自动化测试待用户恢复测试后补跑。
