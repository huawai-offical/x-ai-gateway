# TASK-20260514-024 OpenAI Containers 与 Code Interpreter 文件

状态：Backlog  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 背景

官方 Containers 与 container files 支撑 code_interpreter 等工具资源。当前项目没有 Containers 公开入口，也没有把 Responses tool resource 与 container file lifecycle 打通。

## 目标

- 覆盖 containers create/list/get/delete。
- 覆盖 container files create/list/get/content/delete。
- 将 Responses code_interpreter tool resource 与 container/file lineage 关联。
- 增加资源配额、清理和审计。

## 非目标

- 不在本项目内运行任意用户代码。
- 不复刻 OpenAI sandbox，只代理或编排官方 API。

## 输入

- 官方 Containers API Reference。
- Responses tool resources、Files lifecycle、billing/observability。

## 输出

- Containers controllers/services。
- container files lineage。
- 安全与配额策略。

## 影响范围

- OpenAI resource ingress、security policy、resource storage、admin observability、request audit。

## 依赖

- `TASK-20260514-018` Responses native。
- `TASK-20260514-030` headers/errors/audit 基线。

## 风险

- Code interpreter 文件可能包含敏感数据。
- Container 生命周期和删除策略必须明确，避免成本失控。

## 验收标准

- Containers 与 container files 官方路径具备实现或明确降级。
- code_interpreter tool resource 不再静默丢失。
- 删除、内容读取和权限隔离有 negative tests。

## 测试边界

- Controller/service tests。
- 权限隔离 tests。
- 可选真实 smoke：create container + upload file + read/delete。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

