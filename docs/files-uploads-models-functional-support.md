# Files、Uploads、Models 功能性支撑面

状态：Implemented (tests deferred)
日期：2026-05-21
关联任务：[TASK-20260514-021](../tasks/done/TASK-20260514-021-openai-files-uploads-models-functional-support.md)

## 范围原则

Files、Uploads、Models 只作为对话、tools、RAG/file_search 与模型发现的支撑能力，不恢复 OpenAI 官方 Files/Uploads/Batches/Fine-tuning 全量对象生命周期。

## 支撑端点

| 能力 | 端点 | 当前边界 |
| --- | --- | --- |
| Files list | `GET /v1/files` | 支持 `purpose`、`limit`、`after`、`order`，返回 `object=list`、`data`、`has_more`、`first_id`、`last_id`。 |
| Files create | `POST /v1/files` | multipart `file` 与可选 `purpose`，创建 gateway-local file，可用于对话上下文、RAG 和 Vector Store attachment。 |
| Files retrieve/delete/content | `GET /v1/files/{fileId}`、`DELETE /v1/files/{fileId}`、`GET /v1/files/{fileId}/content` | 以 Distributed Key 隔离本地文件对象，content 返回原始二进制。 |
| Uploads lifecycle | `/v1/uploads*` | 保留 create/get/parts/complete/cancel 的 gateway-local orchestration，用于 multipart assembly 和文件工作流支撑。 |
| Models discovery | `GET /v1/models`、`GET /v1/models/{model}` | 只做当前 Distributed Key 可访问模型发现，不保留 fine-tuned owner-role delete 扩展。 |

## 范围外

| 能力 | 当前处理 |
| --- | --- |
| OpenAI `/v1/batches` | 不在 public OpenAPI、conformance matrix 或未来开发目标中；Vector Store File Batches 仍是 file_search ingestion 支撑能力，不等同于 OpenAI Batches。 |
| Fine-tuning lifecycle | 不进入当前功能性服务 API。 |
| `DELETE /v1/models/{model}` fine-tuned owner-role passthrough | 不继续扩展；Models 只保留 list/get 功能性模型发现。 |

## 验收边界

- Files list 参数和 public OpenAPI 描述一致。
- public OpenAPI 不声明 `/v1/batches` 或 `/v1/fine_tuning/jobs*`。
- Models 公开入口只保留 list/get。
- 当前按用户要求不执行单元测试与真实 smoke；后续真实 key 验证由 `TASK-20260514-031` 承接。
