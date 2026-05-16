# TASK-20260514-026 OpenAI Evals、Graders 与 Runs API

状态：Backlog  
优先级：Medium  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 背景

当前项目没有 Evals API。官方 Evals/Graders/Runs 是模型质量评估、数据集、输出项和 grader 定义的完整资源族。

## 目标

- 覆盖 evals create/list/get/update/delete。
- 覆盖 eval runs create/list/get/cancel/delete 和 output items。
- 覆盖 graders validate/run。
- 与 Files、Fine-tuning、Admin usage 建立引用和成本记录。

## 非目标

- 不在本任务内设计自有评测平台 UI。
- 不自研 grader 执行器替代官方 API。

## 输入

- 官方 Evals API Reference。
- Files lifecycle、request logs、billing rollup。

## 输出

- Evals/Graders controllers/services。
- Runs 状态机和 output item pagination。
- conformance tests。

## 影响范围

- OpenAI resource ingress、async lifecycle、billing、admin observability。

## 依赖

- `TASK-20260514-021` Files lifecycle。
- `TASK-20260514-022` Fine-tuning lineage。

## 风险

- Eval runs 可能高成本、长耗时。
- Grader 输入输出需要严格脱敏和审计。

## 验收标准

- Evals 资源族有完整 coverage matrix。
- Runs cancel/delete 和 output item pagination 可测试。
- Grader validate/run 有 positive/negative tests。

## 测试边界

- Controller/service tests。
- Mock async lifecycle tests。
- 真实 smoke 只读优先。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

