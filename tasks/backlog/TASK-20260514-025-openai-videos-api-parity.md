# TASK-20260514-025 OpenAI Videos API 兼容面

状态：Backlog  
优先级：Medium  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 背景

项目已有自定义 `/api/v1/videos/*` media task，但这不等同于 OpenAI 官方 Videos API。要全量覆盖 OpenAI API，需要单独对齐官方视频生成、编辑、remix、内容和删除语义。

## 目标

- 覆盖 OpenAI Videos create/get/list/delete/content 及 edit/remix 类 endpoint。
- 区分本项目 media task API 与 OpenAI 官方 Videos API。
- 建立视频任务成本、异步状态和文件产物 lineage。

## 非目标

- 不替代现有 provider-specific media task adapters。
- 不承诺非 OpenAI provider 的视频 API 与 OpenAI Videos 完全一致。

## 输入

- 官方 Videos API Reference。
- 当前 `GatewayMediaTasksController` 与 media provider executors。

## 输出

- OpenAI Videos compatibility matrix。
- 官方 Videos ingress 与 executor。
- public OpenAPI 与 docs 区分两套视频 API。

## 影响范围

- media task routing、resource lineage、billing、public docs、portal status。

## 依赖

- `TASK-20260514-031` 成本与 smoke 预算。

## 风险

- 视频生成成本高且耗时。
- 官方视频 API 可能为 preview，字段变化快。

## 验收标准

- OpenAI 官方 Videos endpoint 不与本项目自定义 `/api/v1/videos` 混淆。
- 视频任务创建、状态查询、内容读取、取消/删除有测试边界。
- 真实 smoke 默认受预算控制。

## 测试边界

- Mock executor tests。
- Async lifecycle tests。
- 可选真实 smoke：低成本或 skipped。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

