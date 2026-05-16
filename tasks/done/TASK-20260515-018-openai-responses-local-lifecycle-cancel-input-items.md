# TASK-20260515-018 OpenAI Responses 本地生命周期 cancel/input_items 基线

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)  
上游来源：[TASK-20260514-013](../backlog/TASK-20260514-013-openai-chat-responses-native-parity.md)、OpenAI Responses API Reference

## 背景

`/v1/responses` 当前已有 create、get、delete，本轮已完成 OpenAI Direct 非流式 native create 基线。但 `TASK-20260514-018` 还要求覆盖 lifecycle endpoints。根据 OpenAI Responses API Reference，`POST /v1/responses/{response_id}/cancel` 用于取消 background response，`GET /v1/responses/{response_id}/input_items` 返回该 response 的 input item list，并支持 `after`、`limit`、`order` 分页。本切片先为本地 stored response 补齐可验证的 cancel 和 input_items 行为，作为完整 lifecycle 的下一步地基。

## 目标

- 新增 `POST /v1/responses/{response_id}/cancel` 本地 stored response 状态流转。
- 新增 `GET /v1/responses/{response_id}/input_items`，从原始 request payload 生成 OpenAI list envelope。
- `input_items` 支持 `after`、`limit`、`order`，默认 `limit=20`、`order=desc`，范围 1 到 100。
- 保持 distributed key 隔离，不能读取或取消其他 key 的 response。
- 增加 controller 和 resource service 回归，证明 lifecycle endpoints 可用。

## 非目标

- 不在本切片实现 `responses/input_tokens`、compact 或 count。
- 不在本切片调用 OpenAI 远端 cancel；仅覆盖本地 stored response lifecycle。
- 不改变 Chat stored completion 的 message list 语义。

## 输入

- `GatewayAsyncResourceService.storeResponse` 保存的 request/response payload。
- OpenAI Responses API Reference 中 cancel 与 input_items endpoint 语义。

## 输出

- `GatewayAsyncResourceService.cancelResponse`。
- `GatewayAsyncResourceService.listResponseInputItems`。
- `OpenAiResponsesController` 新增 cancel 与 input_items endpoint。
- 单元测试覆盖分页、状态流转和 controller routing。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceStoredChatTests.java`

## 依赖

- `TASK-20260515-017` OpenAI Responses native create 基线。
- `TASK-20260515-010` list pagination envelope 基线。

## 风险

- OpenAI input item 类型很多，本切片必须保留原始 item JSON，避免只支持文本 happy path。
- 默认 order 与 Chat messages 不同，Responses input_items 默认按官方语义使用 `desc`。
- 已完成 response 是否允许 cancel 需要清晰边界；本切片只允许 `background=true` 且状态未终态的 response 取消。

## 验收标准

- `POST /v1/responses/{id}/cancel` 对 background/in_progress response 返回 object=response、status=cancelled，并写回实体状态与事件。
- 非 background 或已完成 response cancel 返回明确错误。
- `GET /v1/responses/{id}/input_items` 返回 `object=list`、`data`、`first_id`、`last_id`、`has_more`。
- `input_items` 支持 string input、message object、item array，且保留原始 item 内容。
- `limit` 越界和非法 `order` 使用现有 OpenAI-style error envelope。

## 测试边界

- Controller WebFlux tests。
- Resource service tests。
- 不访问真实 OpenAI 远端。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 实现结果

- `OpenAiResponsesController` 新增：
  - `POST /v1/responses/{responseId}/cancel`
  - `GET /v1/responses/{responseId}/input_items`
- `GatewayAsyncResourceService` 新增本地 stored Response lifecycle：
  - `cancelResponse`：仅允许 `background=true` 且未进入终态的 Response 取消，成功后写回 `status=cancelled`、`cancelled_at` 和 metadata event。
  - `listResponseInputItems`：从原始 `input` 生成 `object=list` envelope，支持 string input、message object、item array、`after`、`limit`、`order`。
- `DELETE /v1/responses/{responseId}` 返回对象对齐为 `object=response`、`deleted=true`。
- 公开文档与 OpenAPI snapshot 已补充 Responses retrieve/delete/cancel/input_items。

## 验证结果

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceStoredChatTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"
```

结果：通过。

## 遗留边界

- 本切片只覆盖本地 stored Response lifecycle，不调用 OpenAI 远端 cancel。
- `responses/input_tokens`、compact、count、streaming raw SSE passthrough 和原始 Responses 对象完整透传仍在 `TASK-20260514-018` 后续切片。
- `input_items` 的 `include` 参数当前保留为后续原生远端/完整对象透传切片，不在本地 list 中模拟额外输出字段。
