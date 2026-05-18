# TASK-20260516-001 OpenAI Responses 远端生命周期 Passthrough 基线

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)  
上游来源：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)、[TASK-20260515-017](../done/TASK-20260515-017-openai-responses-native-http-create-baseline.md)、[TASK-20260515-022](../done/TASK-20260515-022-openai-responses-native-json-passthrough-baseline.md)、[TASK-20260515-023](../done/TASK-20260515-023-openai-responses-native-stream-sse-passthrough-baseline.md)

## 背景

OpenAI Direct `/v1/responses` create 和 stream 已分别建立 native HTTP JSON 与 raw SSE passthrough，但 stored Response 的 `GET /v1/responses/{id}`、`DELETE /v1/responses/{id}`、`POST /v1/responses/{id}/cancel`、`GET /v1/responses/{id}/input_items` 仍只读取本地 emulated 对象。当前 `gateway_async_resource` 已有 `upstream_object_id`、`credential_id`、`site_profile_id` 可承载上游 lineage，但 `storeResponse` 还没有把 native create 的上游 Response id 与凭证绑定落库，导致后续 lifecycle 无法同步真实 OpenAI 远端状态。

## 目标

- 在 OpenAI Direct native Responses create 且 `store=true` 时，为本地 stored Response 写入上游 Response id、credential、site profile、resolved/public model 等 lineage metadata。
- 对带上游 lineage 的 stored Response，优先通过绑定凭证调用 OpenAI `/v1/responses/{upstream_id}` lifecycle endpoint。
- 覆盖远端 retrieve、delete、cancel、input_items；retrieve 与 input_items 保留 `include` query，input_items 保留 `after`、`limit`、`order`。
- 远端返回对象写回本地缓存时继续把外部 id 重写为本地 `resp_...`，避免向客户暴露上游对象 id。
- 本地 emulated stored Response 行为保持不变；没有上游 lineage 的对象继续走本地 lifecycle。

## 非目标

- 不支持任意未知远端 `resp_...` id 的无 lineage 盲路由；该路径缺少 model/credential 依据，后续可设计显式 model 或资源绑定发现机制。
- 不实现 `responses/input_tokens` 精确 tokenizer/native passthrough。
- 不实现远端 `compact` passthrough。
- 不改变 OpenAI-compatible Generic、Azure、Claude、Gemini、Ollama 的 Responses lifecycle 行为。

## 输入

- OpenAI Responses lifecycle API reference。
- `OpenAiResponsesController`、`OpenAiResponsesEncoder`。
- `GatewayAsyncResourceService` 与 `gateway_async_resource` lineage 字段。
- `CanonicalExecutionResult.routeSelection()` 中的 credential/site profile/provider 选择结果。

## 输出

- stored Response upstream lineage 持久化。
- Response retrieve/delete/cancel/input_items 远端 passthrough 与本地缓存同步。
- controller/service 单元测试、公开文档与任务索引更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/InteropFeature.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `docs/public-api-compatibility.md`
- `docs/openapi/public-openapi.json`

## 依赖

- `TASK-20260515-017` 已提供 OpenAI Direct native create。
- `TASK-20260515-022` 已保留 upstream raw JSON，便于提取 upstream Response id。
- `TASK-20260515-023` 已确认 native Responses 与 canonical fallback 双轨边界。

## 风险

- 本地 id 与上游 id 都可能以 `resp_` 开头，不能只靠 id 前缀判断远端对象；必须以本地 `gateway_async_resource` lineage 为准。
- delete/cancel 为状态变更操作，如果远端成功、本地保存失败，会产生短暂一致性风险；实现需要先拿远端响应，再同步本地状态和 metadata event。
- retrieve/input_items 的 `include` 为可重复 query 参数，转发时不能合并成破坏官方语义的单个字符串。
- 上游错误 envelope 不在本切片内重写为本地错误对象，仍交由已有异常/错误规则链路处理。

## 验收标准

- OpenAI Direct native create + `store=true` 后，本地 stored Response metadata 记录 `object_mode=upstream_response_with_local_lineage`、`upstream_object_id`、`credential_id`、`site_profile_id`。
- 对上述对象执行 retrieve/cancel/input_items/delete 时，请求路径指向上游真实 `/v1/responses/{upstream_id}` 系列 endpoint，并复用原凭证认证。
- retrieve/cancel 返回的 Response 对象 id 被重写为本地 id，状态和 metadata 同步落库。
- delete 成功后本地对象标记 deleted，返回本地 id 的 deleted envelope。
- 无 upstream lineage 的本地 stored Response 继续通过现有本地实现，既有测试不回归。

## 测试边界

- 不访问真实 OpenAI 远端，使用 `WebClient` fake `ExchangeFunction` 验证 method、path、query 与响应同步。
- service tests 覆盖 lineage 持久化、retrieve、cancel、delete、input_items query passthrough。
- controller tests 覆盖 `include` 参数传入 service 的新签名。
- docs tests 覆盖公开文档和 OpenAPI snapshot。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
- OpenAI Responses create: https://developers.openai.com/api/reference/resources/responses/methods/create
- OpenAI Responses streaming/lifecycle reference: https://platform.openai.com/docs/api-reference/responses

## 实现结果

- `GatewayAsyncResourceService.storeResponse` 新增带 `RouteSelectionResult` 的重载；OpenAI Direct native Responses create + `store=true` 会把 upstream Response id、credential id、site profile id、provider type、public model、resolved model 与 upstream status 写入 `gateway_async_resource.metadata_json`，并同步 `upstream_object_id` 字段。
- `OpenAiResponsesController` 在 stored Responses create 时把 `response.routeSelection()` 传入资源存储，retrieve 和 input_items 也会把 `include` query 传入 service。
- 带 upstream lineage 的 stored Response 已支持：
  - `GET /v1/responses/{id}`：使用原 credential 调上游 retrieve，返回前把 id 重写为本地 `resp_...` 并同步状态。
  - `POST /v1/responses/{id}/cancel`：使用原 credential 调上游 cancel，返回前把 id 重写为本地 `resp_...` 并同步状态。
  - `GET /v1/responses/{id}/input_items`：使用原 credential 调上游 input_items，并原样转发 `after`、重复 `include`、`limit`、`order`。
  - `DELETE /v1/responses/{id}`：使用原 credential 调上游 delete，成功后本地对象标记 deleted 并返回本地 id 的 deleted envelope。
- 无 upstream lineage 的 stored Response 保持原本地 lifecycle 行为，避免任意未知远端 `resp_...` id 发生无模型盲路由。
- 公开 docs bundle、OpenAPI snapshot 与兼容文档已标注远端 lifecycle passthrough 边界。

## 验证情况

- `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests"`
- `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`

## 遗留问题

- 本切片不做任意未知远端 `resp_...` id 的无 lineage 盲路由；后续若需要支持，需要在请求中提供明确 model/credential 或建立远端对象发现机制。
- `responses/input_tokens` native passthrough 已由 [TASK-20260516-002](TASK-20260516-002-openai-responses-input-tokens-native-passthrough.md) 闭环；`responses/compact` native passthrough 已由 [TASK-20260516-003](TASK-20260516-003-openai-responses-compact-native-passthrough.md) 闭环。
