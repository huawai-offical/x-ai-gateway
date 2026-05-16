# TASK-20260514-023 OpenAI Vector Stores 全栈兼容

状态：Backlog  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-016](TASK-20260514-016-openai-full-api-coverage-parent.md)  
上游来源：[TASK-20260514-014](TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 背景

当前项目没有 Vector Stores 公开入口。官方 Vector Stores 是 file_search、Responses tools 和长上下文检索的关键资源族。

## 目标

- 覆盖 vector stores create/list/get/update/delete/search。
- 覆盖 vector store files create/list/get/delete/content。
- 覆盖 file batches create/get/cancel/list。
- 与 Responses file_search tool 建立资源引用和权限校验。

## 非目标

- 不自研向量数据库替代 OpenAI Vector Stores，除非明确作为本地模式。
- 不实现非 OpenAI provider 的 vector store 私有 API。

## 输入

- 官方 Vector Stores API Reference。
- Files lifecycle、Responses tools、resource storage。

## 输出

- Vector Stores controllers/services。
- file_search tool resource binding。
- pagination、polling、delete 和 search tests。

## 影响范围

- OpenAI ingress、resource storage、tool registry、portal/admin resource views、billing usage。

## 依赖

- `TASK-20260514-018` Responses native。
- `TASK-20260514-021` Files lifecycle。

## 风险

- 文件内容和向量资源长期存储涉及隐私、成本和清理策略。
- Search 结果结构需要保持官方兼容。

## 验收标准

- Vector Stores 全部官方路径有 supported/partial/out-of-scope 状态。
- file_search tool 能引用 vector_store_ids。
- 批次上传、polling、cancel、delete 可测试。

## 测试边界

- Controller/service tests。
- File batch 状态机 tests。
- 可选真实 smoke：create store + attach file + search + delete。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

