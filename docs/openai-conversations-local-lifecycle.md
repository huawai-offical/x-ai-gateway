# OpenAI Conversations 本地 Lifecycle

状态：Active  
关联任务：[TASK-20260516-015](../tasks/done/TASK-20260516-015-openai-conversations-local-lifecycle.md)

## 兼容范围

本地 gateway 现支持 OpenAI-compatible Conversations lifecycle：

- `POST /v1/conversations`
- `GET /v1/conversations/{conversationId}`
- `POST /v1/conversations/{conversationId}`
- `DELETE /v1/conversations/{conversationId}`
- `POST /v1/conversations/{conversationId}/items`
- `GET /v1/conversations/{conversationId}/items`
- `GET /v1/conversations/{conversationId}/items/{itemId}`
- `DELETE /v1/conversations/{conversationId}/items/{itemId}`

## 本地 Lineage

- Conversation 保存为 `GatewayAsyncResourceType.CONVERSATION`，resource key 使用 `conv_...`。
- Conversation Item 保存为 `GatewayAsyncResourceType.CONVERSATION_ITEM`，parent conversation id 写入 `upstreamObjectId`。
- Item payload 尽量保留客户端原始 JSON，只补齐缺失的 `id`、`type` 和 `status`。
- 删除 conversation 只软删除 conversation 自身，不级联删除 item lineage。

## 参数边界

- `metadata` 必须是 JSON object，最多 16 个键值对；key 最长 64，value 必须是字符串且最长 512。
- 初始 `items` 与追加 `items` 一次最多 20 条。
- `GET /items` 支持 `after`、`include`、`limit`、`order`。
- `limit` 合法范围为 1 到 100，默认 20。
- `order` 仅支持 `asc` 或 `desc`，默认 `desc`。
- 本地 baseline 接受 `include` 作为 no-op 兼容参数，不做外部资源展开。

## 非目标

- 不声明 OpenAI Direct Conversations 上游 passthrough。
- 不把 Responses create 的 `conversation` 参数自动绑定到本地 conversation。
- 不执行 hosted tools、MCP/custom tools、Vector Stores 或 Containers。

## 验证

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceConversationsTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiConversationsControllerTests"
```
