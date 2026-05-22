# TASK-20260516-015 OpenAI Conversations 本地 Lifecycle

状态：Done
优先级：High
类型：子任务
父任务：[TASK-20260514-019](../done/TASK-20260514-019-openai-conversations-webhooks-tools.md)
上游来源：[REQ-20260516-015](../../docs/requirements/REQ-20260516-015-openai-conversations-local-lifecycle.md)

## 背景

`TASK-20260514-019` 仍缺 OpenAI Conversations 与 Conversation Items lifecycle。官方 API 已公开 conversation create/retrieve/update/delete，以及 item create/retrieve/delete/list。当前仓库仅 path matcher 识别 `/v1/conversations`，没有 controller/service lifecycle 与公开事实源。

## 目标

- 增加 `/v1/conversations` 与 `/v1/conversations/{conversationId}/items` 本地 lifecycle。
- 使用 `gateway_async_resource` 保存 conversation 与 item 的 local lineage。
- 支持 metadata、initial items、items create/list/retrieve/delete、pagination query。
- 同步公开文档、OpenAPI、provider catalog、报告和任务索引。

## 非目标

- 不做 OpenAI Direct Conversations 上游 passthrough。
- 不实现 hosted tools、MCP/custom tools 或 external include 展开。
- 不改造 Responses create 的 `conversation` 参数执行语义；本轮只补独立 Conversations 资源族。

## 输入

- OpenAI API Reference Conversations 与 Items 端点。
- 当前 `GatewayAsyncResourceService` 的 stored Response / stored Chat lifecycle。
- `TASK-20260514-019` 的剩余切片边界。

## 输出

- `OpenAiConversationsController`。
- `GatewayAsyncResourceService` conversation/item methods。
- `GatewayAsyncResourceType` 与 repository 查询扩展。
- Controller/service/docs/provider catalog/OpenAPI 测试。
- 更新后的 docs/tasks 报告。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/`
- `docs/`、`tasks/`、`src/main/resources/provider-catalog.json`

## 依赖

- `TASK-20260515-010` OpenAI list pagination envelope。
- `TASK-20260515-015` OpenAI protocol path matcher。
- `TASK-20260516-014` Responses tools registry boundary。

## 风险

- Item object union 很宽，需避免窄化或破坏客户端原始 item。
- 批量 create item 需要有上限、重复检测和失败原子性。
- Conversation delete 与 item delete 的本地语义需要清晰声明。

## 验收标准

- `POST /v1/conversations` 支持 optional `metadata` 与最多 20 条 `items`。
- `GET/POST/DELETE /v1/conversations/{conversationId}` 返回 OpenAI-compatible shape。
- `POST/GET /v1/conversations/{conversationId}/items` 与 `GET/DELETE /v1/conversations/{conversationId}/items/{itemId}` 可用。
- Item list 默认 `order=desc`、`limit=20`，支持 `after`、`limit`、`order`、`include`。
- Duplicate item id、非法 limit/order、单次超过 20 条均返回 OpenAI-style error。
- 文档、OpenAPI、provider catalog、报告与任务状态同步。

## 测试边界

- `GatewayAsyncResourceConversationsTests`
- `OpenAiConversationsControllerTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- `ProviderCatalogLoaderTests`

## 关联文档

- [REQ-20260516-015](../../docs/requirements/REQ-20260516-015-openai-conversations-local-lifecycle.md)
- [REP-20260514 OpenAI API 完整兼容性深度审计](../../docs/reports/REP-20260514-openai-api-compatibility-deep-audit.md)
- [REP-20260514 OpenAI API 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
- [TASK-20260514-019](../done/TASK-20260514-019-openai-conversations-webhooks-tools.md)

## 当前状态

- 2026-05-16：已闭环，等待移动到 `tasks/done/`。

## 实现结果

- 新增 `OpenAiConversationsController`：
  - `POST /v1/conversations`
  - `GET /v1/conversations/{conversationId}`
  - `POST /v1/conversations/{conversationId}`
  - `DELETE /v1/conversations/{conversationId}`
  - `POST /v1/conversations/{conversationId}/items`
  - `GET /v1/conversations/{conversationId}/items`
  - `GET /v1/conversations/{conversationId}/items/{itemId}`
  - `DELETE /v1/conversations/{conversationId}/items/{itemId}`
- `GatewayAsyncResourceService` 增加 conversation/item local lifecycle，Conversation 与 Item 分别保存为 `CONVERSATION`、`CONVERSATION_ITEM`。
- Item `upstreamObjectId` 绑定 parent conversation，list 使用数据库 cursor 查询，支持 `after`、`limit`、`order`。
- 批量 item create 限制 20 条，校验 duplicate item id、metadata shape 和 list 参数范围。
- Public docs、OpenAPI、provider catalog、reports 已同步 `openai.conversations-local-lifecycle`。

## 验证记录

通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceConversationsTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiConversationsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceStoredChatTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.infra.config.web.OpenAiProtocolPathMatcherTests"
```

## 遗留问题

- OpenAI Direct Conversations 上游 passthrough 未实现。
- Responses create 的 `conversation` 参数绑定本地 conversation 未实现。
- Conversation Item `include` 仅作为 no-op 兼容参数接受。
