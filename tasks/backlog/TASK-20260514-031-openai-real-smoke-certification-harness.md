# TASK-20260514-031 OpenAI 真实 Smoke 与认证成本防护

状态：Backlog  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-015](TASK-20260514-015-openai-openapi-conformance-truth-source-hardening.md)

## 背景

全量兼容不能只靠 mock。OpenAI API 中 Fine-tuning、Evals、Videos、Vector Stores、Administration 等需要真实 key、组织权限、成本预算和安全隔离。当前项目需要一套可重复、可跳过、可审计的认证与 smoke 体系。

## 目标

- 建立 OpenAI real smoke harness，支持按资源族选择执行。
- 建立 secret vault、权限探测、成本预算、速率限制和 skipped reason。
- 支持 record/replay，把成功响应脱敏后作为 conformance fixture。
- 在 admin/CI 中输出 coverage certification 报告。

## 非目标

- 不把用户提供的真实 key 写入代码或仓库。
- 不默认执行高成本写操作。

## 输入

- OpenAI API key、organization/project 权限。
- 全部 OpenAI coverage matrix。
- 现有 provider smoke/pricing sync 机制。

## 输出

- Real smoke runner。
- 成本预算和权限分类。
- 脱敏 fixture、认证报告和失败分类。

## 影响范围

- Test harness、admin smoke UI、credential storage、audit logs、conformance fixtures、docs。

## 依赖

- `TASK-20260514-030` 横切协议兼容。
- 真实 key 和组织权限。

## 风险

- 高成本 API 被误触发。
- 真实响应可能包含敏感信息，必须脱敏。
- Rate limit 或权限不足不能误判为功能失败。

## 验收标准

- Smoke 支持 `PASS/FAIL/SKIPPED/UNSUPPORTED/NO_PERMISSION/BUDGET_BLOCKED` 分类。
- 所有真实请求有审计、成本上限和脱敏记录。
- 至少覆盖 Chat、Responses、Files、Batches、Vector Stores、Realtime client secret 的 smoke 分类。

## 测试边界

- Harness unit tests。
- Credential redaction tests。
- 真实 smoke 手动或受控 CI 执行。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 已完成切片

- [TASK-20260515-016 OpenAI/Codex Real Smoke 分类与预算阻断基线](../done/TASK-20260515-016-openai-codex-real-smoke-classification-budget-guard.md)：Codex App API responses smoke 已输出标准 `classification` 与 `skippedReason`；usage probe 在额度、速率、权限阻断时不继续发起真实 responses POST；账号池详情页已展示标准分类。

## 剩余切片

- OpenAI Direct key vault、权限探测与 secret 引用。
- Chat、Responses、Files、Batches、Vector Stores、Realtime client secret 的资源族 smoke runner。
- record/replay fixture 脱敏保存与 certification report。
