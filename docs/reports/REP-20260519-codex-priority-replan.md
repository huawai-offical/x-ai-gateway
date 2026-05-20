# REP-20260519 Codex 最高优先级重排

状态：In Progress  
日期：2026-05-19  
关联需求：[REQ-20260519-002](../requirements/REQ-20260519-002-codex-priority-functional-service-api.md)

## 结论

Codex 相关开放任务提升为当前最高优先级。项目继续遵循 [ADR-0010](../decisions/ADR-0010-functional-service-api-scope.md)：不做官方 API 全量覆盖，只做对话、streaming、tools/function calling、多模态与必要支撑能力。Codex 不再扩展非 Responses 内部 API，不承接 Fine-tuning、Batches、Evals、Admin 等非核心能力。

## 当前 P0 队列

| 排期 | 任务 | 状态 | 执行边界 |
| --- | --- | --- | --- |
| P0-CODEX-01 | [TASK-20260519-002 Codex 功能性服务 API 最高优先推进](../../tasks/done/TASK-20260519-002-codex-priority-functional-service-api.md) | Done | 父任务已归档，Codex-first 执行顺序已锁定 |
| P0-CODEX-02 | [TASK-20260519-002-01 Codex 功能性服务 API 事实源优先收紧](../../tasks/done/TASK-20260519-002-01-codex-functional-truth-source-priority.md) | Done | 第一执行切片已归档；测试按用户当前策略延后 |
| P0-CODEX-03 | [TASK-20260514-029 对话与 Tools OpenAPI、Catalog、Conformance 与 SDK 事实源统一](../../tasks/in-progress/TASK-20260514-029-openai-openapi-catalog-conformance-sdk.md) | In Progress | 当前执行 coverage matrix source 切片 |
| P0-CODEX-04 | [TASK-20260519-002-02 Codex Smoke、Record/Replay 与成本防护复核](../../tasks/done/TASK-20260519-002-02-codex-smoke-record-replay-priority.md) | Done | 第二执行切片已归档；测试按用户当前策略延后 |
| P0-CODEX-05 | [TASK-20260514-031 OpenAI 真实 Smoke 与认证成本防护](../../tasks/backlog/TASK-20260514-031-openai-real-smoke-certification-harness.md) | Backlog | 复用既有 smoke 分类、预算阻断与 fixture 机制 |

## 剩余任务判断

- Codex 旧 UI/运营主链大多已 Done：账号导入、反代、配额刷新、观测投影、批量恢复、审计与前端联调任务已经归档。
- 当前真正需要优先推进的是“对外 API 承诺和事实源是否仍残留全量覆盖口径”，因此 `TASK-20260514-029` 被提升为 Codex 第一执行切片。
- Codex smoke 已有基础切片 `TASK-20260515-016`，但在新的功能性 API 范围下仍需要复核 record/replay、成本阻断、文档与支持矩阵，归入第二执行切片。

## 执行顺序

1. 先收紧 Codex facts：public OpenAPI、provider catalog、conformance accepted exceptions、SDK 示例和门户文档统一只声明功能性服务 API。
2. 再复核 Codex smoke：低成本对话/Responses smoke、record/replay fixture、secret 脱敏、预算阻断和 skipped reason。
3. 最后回填用户可见文档：接入说明、兼容等级、非目标说明和后续验证证据。

## 本轮验证

按用户要求，本轮不执行测试、不跑真实 smoke、不跑浏览器回归。后续进入实现切片时必须补回对应验证命令或手工检查点。

## 2026-05-19 进展

- 已新增 [Codex 功能性服务 API 事实源](../codex-functional-service-api-facts.md)，明确 Codex 不进入通用 provider catalog preset，只保留 Responses smoke/反代边界。
- 已收紧 public docs bundle、OpenAPI 描述、native compatibility matrix 和本地 Markdown 公开说明，避免把 Codex 与 Anthropic/Gemini/Vertex 的 embeddings/files 支撑面混在一句里。
- 已加入 `codex.responses-smoke-boundary` conformance 标识和后续测试断言；`TASK-20260519-002-01` 已按当前“不跑测试、全速推进”策略归档，自动化测试待恢复后补跑。
- `TASK-20260519-002-02` 已补上 Codex Responses smoke 的版本化 `recordReplayFixture`、脱敏 sample fixture 与离线 verifier；默认 replay policy 禁止真实网络、billable 和 write 自动执行，并已按当前测试策略归档。
- 编译级验证 `.\gradlew.bat compileJava compileTestJava -x test` 通过；按用户要求未运行测试。
- 目标文件范围的 `git diff --check` 已通过；全仓库 diff check 仍受既有 `web/src/features/keys/keys-page.tsx` EOF 空行影响，本轮未改动该无关文件。
- `TASK-20260514-029` 已转入 In Progress，并完成 [TASK-20260514-029-01](../../tasks/done/TASK-20260514-029-01-functional-service-api-coverage-matrix-source.md)：建立功能性服务 API coverage matrix source。
