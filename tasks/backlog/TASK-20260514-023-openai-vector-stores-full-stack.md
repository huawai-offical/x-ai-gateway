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
- 覆盖 file batches create/get/cancel/list files。
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

## 子任务进度

- 已完成：[TASK-20260517-003 OpenAI Vector Stores 本地 Lifecycle 基线](../done/TASK-20260517-003-openai-vector-stores-local-lifecycle-baseline.md)。已补 `POST/GET /v1/vector_stores` 与 `GET/POST/DELETE /v1/vector_stores/{vectorStoreId}`，本地 `vector_store` 对象保存到 `gateway_async_resource` 的 `VECTOR_STORE`，支持 list envelope、`after/limit/order`、metadata/name/expiry 更新和软删除。
- 已完成：[TASK-20260517-004 OpenAI Vector Store Files 本地 Attachment Lifecycle 基线](../done/TASK-20260517-004-openai-vector-store-files-local-attachment-lifecycle.md)。已补 `POST/GET /v1/vector_stores/{vectorStoreId}/files` 与 `GET/DELETE /v1/vector_stores/{vectorStoreId}/files/{fileId}`，本地 attachment 保存为 `VECTOR_STORE_FILE` child resource，并在 create/delete 时同步 parent `file_counts`。
- 已完成：[TASK-20260517-005 OpenAI Vector Store File Batches 本地 Lifecycle 基线](../done/TASK-20260517-005-openai-vector-store-file-batches-local-lifecycle.md)。已补 `POST /v1/vector_stores/{vectorStoreId}/file_batches`、`GET/POST cancel /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}` 与 `GET /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files`，本地 batch 保存为 `VECTOR_STORE_FILE_BATCH`，批量 create 会先完成非空、去重和已存在 attachment 校验，再创建 batch 与 file attachment。
- 已完成：[TASK-20260518-001 OpenAI Vector Store File Content 本地读取基线](../done/TASK-20260518-001-openai-vector-store-file-content-local-read-baseline.md)。已补 `GET /v1/vector_stores/{vectorStoreId}/files/{fileId}/content`，按当前 Distributed Key、parent vector store 与 active attachment 校验后读取 gateway file，并返回 `vector_store.file_content.page` 本地文本页。
- 已完成：[TASK-20260518-002 OpenAI Vector Store Search 本地文本检索基线](../done/TASK-20260518-002-openai-vector-store-search-local-text-baseline.md)。已补 `POST /v1/vector_stores/{vectorStoreId}/search`，按当前 Distributed Key、parent vector store、active attachment、attributes filter、`max_num_results` 与 `ranking_options.score_threshold` 搜索可读取 gateway file 文本，并返回 `vector_store.search_results.page`。
- 已完成：[TASK-20260518-003 OpenAI Responses File Search 本地 Vector Store 绑定基线](../done/TASK-20260518-003-openai-responses-file-search-local-vector-store-binding.md)。已让 `/v1/responses` 的 `file_search` tool 校验并引用本地 `vector_store_ids`，复用本地 search 结果注入上下文，并在 canonical mapper/provider 前移除 hosted tool。
- 剩余切片：真实向量入库、语义向量检索、hosted `file_search_call` lifecycle、真实 smoke。
