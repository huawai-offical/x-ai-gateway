# TASK-20260515-004 OpenAI Stored Chat Completions 生命周期基线

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-017](../done/TASK-20260514-017-openai-chat-completions-full-parity.md)
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[TASK-20260515-003](../done/TASK-20260515-003-openai-chat-create-parameter-parity-foundation.md)

## 背景

官方 Chat Completions API 不只有 `POST /v1/chat/completions`，还包含 stored Chat Completions 的 list/get/update/delete/messages 生命周期。当前项目已经能在 `Responses` 的 `store=true` 路径中复用 `GatewayAsyncResourceService` 保存响应对象，但 Chat Completions 只实现 create，`store=true` 不会落库，也没有对象生命周期入口。

## 目标

- 当 Chat create 请求 `store=true` 时，把 `chat.completion` 响应以租户隔离方式保存为本地异步资源。
- 补齐 `GET /v1/chat/completions`、`GET /v1/chat/completions/{id}`、`POST /v1/chat/completions/{id}`、`DELETE /v1/chat/completions/{id}`、`GET /v1/chat/completions/{id}/messages` 基线。
- update 仅允许更新 `metadata`，与官方当前限制对齐。
- 所有 stored Chat 读取、更新、删除均按 `distributedKeyId` 隔离。
- 增加 controller 测试覆盖 create-store、list/get/update/delete/messages。

## 非目标

- 不在本轮实现完整 cursor pagination、metadata query filter 的数据库级优化。
- 不实现跨 provider 上游 stored Chat 同步；本轮是 gateway 本地 contract。
- 不改变 `Responses` 的 stored response 行为。

## 输入

- `OpenAiChatCompletionsController`
- `GatewayAsyncResourceService`
- `GatewayAsyncResourceEntity`
- `GatewayAsyncResourceRepository`
- `OpenAiChatCompletionsControllerTests`

## 输出

- Chat stored lifecycle controller endpoints。
- Chat stored lifecycle service methods。
- WebFlux tests 与任务回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsController.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceStoredChatTests.java`
- `tasks/index.md`

## 依赖

- `TASK-20260515-003` 已完成 Chat create 参数和 `store` 字段接收。
- 现有 `GatewayAsyncResourceService` 的 RESPONSE 类型资源表。

## 风险

- `GatewayAsyncResourceType.RESPONSE` 同时承载 Responses API 与 Chat stored object，list 时必须按响应对象 `object=chat.completion` 过滤。
- 软删除后不能再被 get/list/messages 返回。
- metadata update 不能覆盖其它响应字段。

## 验收标准

- `store=true` 的 Chat create 返回持久化后的 `chatcmpl_` id。
- list 只返回当前 key 的未删除 `chat.completion` 对象。
- get/update/delete/messages 调用都按当前 key 的 `distributedKeyId` 查询。
- update 只更新 `metadata`。
- 定向测试通过。

## 测试边界

- 更新 `OpenAiChatCompletionsControllerTests`。
- 运行 Chat controller、runtime 与横切组合回归。

## 实现结果

- `POST /v1/chat/completions` 在 `store=true` 时会把 OpenAI `chat.completion` 响应保存为 `chatcmpl_` 本地资源，并继续按 `distributedKeyId` 隔离。
- 新增 stored Chat lifecycle 入口：`GET /v1/chat/completions`、`GET /v1/chat/completions/{id}`、`POST /v1/chat/completions/{id}`、`DELETE /v1/chat/completions/{id}`、`GET /v1/chat/completions/{id}/messages`。
- list 支持基线级 `after`、`limit`、`order`、`model` 与 `metadata[key]=value` 过滤；messages list 支持基线级 `after`、`limit` 与 `order`。
- update 只允许替换 `metadata`，delete 使用软删除并返回 OpenAI-style `chat.completion.deleted` 对象。
- `GatewayAsyncResourceType.RESPONSE` 继续复用 Responses 资源表，但 stored Chat list/get/update/delete/messages 均按响应 payload 的 `object=chat.completion` 做对象类型隔离。

## 验证结果

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceStoredChatTests"`
- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.infra.config.web.GlobalApiExceptionHandlerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiModelsControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceStoredChatTests"`

## 遗留问题

- 本轮不做 stored Chat 的数据库级 cursor/metadata 索引优化；如果真实数据规模增长，需要拆到 pagination 横切任务中补二级索引或专用查询。
- 本轮不做跨 provider 上游 stored Chat 同步；当前 contract 是 gateway 本地对象生命周期。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
- 官方 Chat Completions API Reference：https://platform.openai.com/docs/api-reference/chat/create-chat-completion
