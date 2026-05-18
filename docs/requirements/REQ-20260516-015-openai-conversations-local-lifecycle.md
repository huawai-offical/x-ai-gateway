# REQ-20260516-015 OpenAI Conversations 本地 Lifecycle 闭环

状态：Done  
日期：2026-05-16  
来源任务：[TASK-20260514-019](../../tasks/backlog/TASK-20260514-019-openai-conversations-webhooks-tools.md)

## 背景

OpenAI Responses API 已公开 Conversations 与 Conversation Items 资源族。官方端点包含：

- `POST /v1/conversations`
- `GET /v1/conversations/{conversation_id}`
- `POST /v1/conversations/{conversation_id}`
- `DELETE /v1/conversations/{conversation_id}`
- `POST /v1/conversations/{conversation_id}/items`
- `GET /v1/conversations/{conversation_id}/items`
- `GET /v1/conversations/{conversation_id}/items/{item_id}`
- `DELETE /v1/conversations/{conversation_id}/items/{item_id}`

当前项目已经把 `/v1/conversations` 纳入 OpenAI protocol path matcher，但还没有 controller、service lifecycle、公开 OpenAPI 和 provider catalog conformance。`TASK-20260514-019` 仍把 Conversations/Items 列为 High 未闭环切片。

## 目标

- 增加 OpenAI-compatible Conversations controller，覆盖 conversation create/retrieve/update/delete。
- 增加 Conversation Items create/list/retrieve/delete，支持 `after`、`include`、`limit`、`order` 查询参数。
- 使用 `gateway_async_resource` 保存本地 lineage：conversation 与 item 分开建模，item 通过 parent conversation id 关联。
- 初始 items 和追加 items 均限制一次最多 20 条，避免批量导入失控。
- 将实现边界同步到 docs bundle、public OpenAPI、provider catalog、兼容报告和任务体系。

## 非目标

- 不实现上游 OpenAI Conversations 远端 passthrough；本轮只建立 gateway local lineage。
- 不实现 hosted tools、MCP/custom tools、Vector Stores 或 Containers 的真实执行。
- 不对 item `include` 做真实外部资源展开；本轮只接受并保留 no-op 兼容边界。

## 方案

1. 在 `GatewayAsyncResourceType` 中新增 `CONVERSATION` 与 `CONVERSATION_ITEM`。
2. 在 `GatewayAsyncResourceService` 中新增本地 conversation lifecycle：
   - `createConversation(distributedKeyId, requestBody)`
   - `getConversation(conversationId, distributedKeyId)`
   - `updateConversation(conversationId, distributedKeyId, requestBody)`
   - `deleteConversation(conversationId, distributedKeyId)`
   - `createConversationItems(conversationId, distributedKeyId, requestBody, include)`
   - `listConversationItems(conversationId, distributedKeyId, after, include, limit, order)`
   - `getConversationItem(conversationId, itemId, distributedKeyId, include)`
   - `deleteConversationItem(conversationId, itemId, distributedKeyId)`
3. Item 独立保存为 `CONVERSATION_ITEM`，`upstreamObjectId` 写入 parent conversation id；删除 conversation 只软删除 conversation，自身 item 不被级联删除。
4. `limit` 复用 1 到 100 的边界，items create 的批量上限固定 20。
5. OpenAI-style 错误继续由 `GlobalApiExceptionHandler` 根据 `/v1/conversations` path 自动包装。

## 影响范围

- 后端：`GatewayAsyncResourceService`、`GatewayAsyncResourceType`、`GatewayAsyncResourceRepository`、新增 `OpenAiConversationsController`。
- 公开事实源：`PublicDocsBundleService`、`docs/openapi/public-openapi.json`、`docs/public-api-compatibility.md`、`provider-catalog.json`。
- 任务与报告：`TASK-20260514-019`、`TASK-20260516-015`、OpenAI coverage/audit reports、`tasks/index.md`、`docs/index.md`。

## 风险

- Conversation item 类型非常多，本轮如果过度转换 payload 容易破坏客户端原始语义；因此只补 `id/type/status` 等最小字段，尽量保留原始 item。
- 删除 conversation 后 item 是否可继续通过官方 API 读取存在语义细节；本地 lineage 不级联删除 item，但 item endpoint 仍要求 conversation id 可定位。
- 批量写入需要限制上限并校验 duplicate id，否则可能污染全局 `resource_key` 唯一约束。

## 验收标准

- Conversation create 能保存 metadata 和初始 items，并返回 `{id, object:"conversation", created_at, metadata}`。
- Conversation retrieve/update/delete 返回官方兼容 shape，删除返回 `conversation.deleted`。
- Items create/list/retrieve/delete 均按当前 DistributedKey 隔离；list 支持默认 `order=desc`、`limit=20`、`after` cursor。
- 一次 create items 超过 20 条或 duplicate item id 返回 OpenAI-style `invalid_request_error`。
- Public docs、OpenAPI、provider catalog 与任务报告同步标记 `openai.conversations-local-lifecycle`。

## 测试边界

- Service 单测覆盖：创建、初始 items、分页排序、cursor、追加、删除 item、duplicate id、批量上限、conversation delete 不级联 item。
- Controller WebFlux 测试覆盖：鉴权转发、query 参数、OpenAI-style 错误。
- Docs/OpenAPI/provider catalog 快照测试覆盖公开事实源。

## 实现结果

- 新增 `OpenAiConversationsController`，覆盖 conversation create/retrieve/update/delete 与 item create/list/retrieve/delete。
- `GatewayAsyncResourceType` 新增 `CONVERSATION`、`CONVERSATION_ITEM`，conversation 和 item 均保存到 `gateway_async_resource`。
- Conversation Item 使用 `upstreamObjectId` 记录 parent conversation id，删除 conversation 不级联删除 item lineage。
- Items create 支持一次最多 20 条；list 支持 `after`、`include`、`limit`、`order`，默认 `limit=20`、`order=desc`。
- 更新 public docs bundle、`docs/openapi/public-openapi.json`、`docs/public-api-compatibility.md`、provider catalog 和 OpenAI coverage reports，公开 conformance 标识 `openai.conversations-local-lifecycle`。

## 验证记录

通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceConversationsTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiConversationsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceStoredChatTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.infra.config.web.OpenAiProtocolPathMatcherTests"
```

## 遗留问题

- 本轮只实现 gateway local lineage，不声明 OpenAI Direct Conversations 上游 passthrough。
- Responses create 的 `conversation` 参数仍未自动绑定到本地 conversation，后续归入 Responses native lifecycle/参数保真切片。
- `include` 在本地 Conversation Items 上是 no-op acceptance，不做外部资源展开。
