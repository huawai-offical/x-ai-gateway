# TASK-20260514-022 OpenAI Fine-tuning 全生命周期

状态：Backlog  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 背景

当前 Fine-tuning 只覆盖 create/list/get/cancel，缺 events、checkpoints、pause/resume、permissions、grader 相关能力和真实状态同步。

## 目标

- 补齐 fine-tuning jobs events/checkpoints/pause/resume/cancel。
- 支持 checkpoint permissions 的 create/list/delete。
- 校准 fine-tuning request 参数、method、hyperparameters、integrations、seed、suffix 等。
- 和 Files、Models lifecycle 打通 lineage。

## 非目标

- 不实现 Evals/Graders 全量接口，交由 `TASK-20260514-026`。
- 不默认执行高成本真实训练，只做低成本或 mock smoke。

## 输入

- 官方 Fine-tuning API Reference。
- `OpenAiFineTuningJobsController`、async resource service、file/model lifecycle。

## 输出

- Fine-tuning lifecycle controllers。
- checkpoint/event persistence 或 passthrough。
- conformance tests 与 smoke 分类。

## 影响范围

- Fine-tuning ingress、async resource storage、model catalog、request logs、admin observability。

## 依赖

- `TASK-20260514-021` Files/Models lifecycle。
- 真实 OpenAI 权限与成本预算。

## 风险

- 真实 fine-tuning 成本高、耗时长。
- 权限和 checkpoint sharing 容易跨租户泄露。

## 验收标准

- Fine-tuning events/checkpoints 不再是 accepted exception。
- pause/resume/cancel 状态迁移可测试。
- 真实 smoke 可在预算不足时 skipped，并记录原因。

## 测试边界

- Controller/service tests。
- 状态迁移 tests。
- 可选真实 smoke：创建最小 job 或只读事件/列表。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

