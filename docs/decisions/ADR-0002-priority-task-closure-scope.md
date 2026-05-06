# ADR-0002 最高优先级任务采用“骨架闭环优先”的交付范围

状态：Accepted  
日期：2026-05-01  
关联需求：[REQ-20260501-002 最高优先级三个任务闭环设计](../requirements/REQ-20260501-002-priority-task-closure-design.md)

## 背景

High 级增强任务中包含供应商目录、非 Chat 资源、Realtime、计费、安全、路由策略等多个方向。直接推进全部任务会导致实现面过大，也难以在一次迭代内形成可靠验收。

## 决策

本轮优先选择并闭环三个基础型任务：

- `TASK-20260501-001 Provider Registry 2.0`
- `TASK-20260501-002 非 Chat 资源族扩展`
- `TASK-20260501-008 路由策略 2.0`

闭环定义为：本地设计完成、代码切片完成、测试覆盖完成、任务和文档回写完成。对长期能力不在本轮假装完成，而是明确记录为后续扩展。

## 理由

- Provider Registry 是后续 adapter、能力矩阵、费用策略的上游事实来源。
- 非 Chat 资源抽象是 Video、Music、Rerank、Task lifecycle、Workbench 的共同前置。
- 路由策略解释性可以直接提升当前系统的可观测性，并为 retry、fallback、熔断配置打基础。

## 后果

- 本轮可以交付可验证的小闭环。
- 后续仍需要拆出 provider adapter、真实 async task executor、前端策略配置、并发熔断模型等任务。
- 本地任务系统将承载后续拆分，不再依赖线上 Linear。
