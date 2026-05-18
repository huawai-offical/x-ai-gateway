# MIG-20260516 Stored Chat Pagination Index

日期：2026-05-16  
关联任务：[TASK-20260516-004](../../tasks/done/TASK-20260516-004-openai-stored-chat-db-pagination-filter-hardening.md)

## 背景

`GET /v1/chat/completions` 已支持 OpenAI-compatible `after`、`limit`、`order`、`model` 与 `metadata[key]` 查询参数。旧实现通过通用 async resource 查询拉取固定窗口后在内存中过滤，真实数据量增长时可能漏掉窗口外的 stored Chat Completion。

## 变更

- 新增 Liquibase changeSet `0050-stored-chat-pagination-index`。
- 在 `gateway_async_resource` 上新增索引 `idx_gateway_async_resource_chat_list`：
  - `distributed_key_id`
  - `resource_type`
  - `resource_key`
  - `request_model`
  - `created_at`
  - `id`

## 目的

该索引用于支撑 stored Chat list 的数据库级候选裁剪与稳定游标分页：

- 按 Distributed Key 和资源类型隔离。
- 按 `chatcmpl_` resource key 前缀裁剪 stored Chat。
- 按 `request_model` 下推 model filter。
- 使用 `created_at + id` 做稳定 asc/desc cursor。

## 非目标

- 不新增数据库方言 JSON/JSONB metadata 索引。
- 不改变表结构字段。
- 不迁移历史 payload。

## 验证

- 已随 `TASK-20260516-004` 通过 targeted tests：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceStoredChatTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"
```
