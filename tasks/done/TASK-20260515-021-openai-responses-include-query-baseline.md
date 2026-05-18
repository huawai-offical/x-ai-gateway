# TASK-20260515-021 OpenAI Responses include Query 参数基线

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)  
上游来源：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)、OpenAI Responses API Reference

## 背景

OpenAI Responses API 的 `GET /v1/responses/{response_id}` 和 `GET /v1/responses/{response_id}/input_items` 都支持 `include` query 参数，用于请求额外字段。当前本地 stored Responses lifecycle 已有 retrieve 和 input_items，但 controller 与公开 OpenAPI 没有声明或接收 `include`，客户端 SDK 如果携带该 query 会缺少明确兼容证据。

## 目标

- `GET /v1/responses/{responseId}` 接收 `include` query 参数。
- `GET /v1/responses/{responseId}/input_items` 接收 `include` query 参数。
- 本地 stored baseline 对 `include` 做 no-op 兼容，不因参数存在而失败；后续 native passthrough 切片再实现远端 include 语义。
- 公开 docs bundle 与 OpenAPI snapshot 标明两个 endpoint 的 `include` query 参数。

## 非目标

- 不在本切片计算或注入 OpenAI 官方 include 扩展字段。
- 不实现 OpenAI Direct retrieve/input_items 远端 passthrough。
- 不改变 stored Response payload shape。

## 输入

- `OpenAiResponsesController` retrieve 与 input_items endpoint。
- `GatewayAsyncResourceService` 当前 stored Response lifecycle。

## 输出

- Controller query 参数兼容。
- Controller tests。
- Public OpenAPI snapshot 与兼容文档更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/docs/PublicOpenApiSnapshotTests.java`
- `docs/openapi/public-openapi.json`
- `docs/public-api-compatibility.md`

## 依赖

- `TASK-20260515-018` stored Responses lifecycle。

## 风险

- 如果 silently ignore 没有文档说明，客户可能误以为 include 已具备远端语义；文档需要明确本地 baseline 是 no-op acceptance。

## 验收标准

- retrieve endpoint 携带 `include=...` 仍返回 stored Response。
- input_items endpoint 携带一个或多个 `include` 仍返回 list envelope。
- OpenAPI snapshot 对两个 endpoint 都声明 `include` query 参数。

## 测试边界

- Controller WebFlux tests。
- Public docs bundle tests。
- Public OpenAPI snapshot tests。
- 不访问真实 OpenAI 远端。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 实现结果

- `GET /v1/responses/{responseId}` 已接收 `include` query 参数。
- `GET /v1/responses/{responseId}/input_items` 已接收一个或多个 `include` query 参数。
- 当前本地 stored baseline 对 `include` 做 no-op acceptance，不改变 stored Response payload。
- 公开 docs bundle、OpenAPI snapshot 与兼容文档已说明 `include` 的本地边界。

## 验证情况

- `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`

## 遗留问题

- 本切片不实现远端 include 语义；OpenAI Direct retrieve/input_items passthrough 仍归属父任务后续 native passthrough 切片。
