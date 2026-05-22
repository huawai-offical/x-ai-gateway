# TASK-20260515-009 OpenAI Idempotency-Key 本地响应持久化与重放基线

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-030](../done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[TASK-20260515-002](../done/TASK-20260515-002-openai-official-headers-idempotency-foundation.md)

## 背景

`TASK-20260515-002` 已把 `Idempotency-Key` 捕获到 canonical metadata，并确保只对 OpenAI Direct 下发官方 header。但网关本地尚未记录非流式创建请求的响应结果；客户端重试同一个 `Idempotency-Key` 时仍可能重复消耗上游额度、重复创建 stored object 或重复写 usage。OpenAI 兼容面需要先建立本地 replay 基线。

## 目标

- 为非流式 `POST /v1/chat/completions` 与 `POST /v1/responses` 建立本地 idempotency 响应记录。
- 记录维度为 distributed key、request path、`Idempotency-Key` 与 request fingerprint。
- 重复 key 且请求体一致时直接返回已记录响应，不再次调用 gateway execution service。
- 重复 key 但请求体不同，在 OpenAI path 返回 `invalid_request_error`。
- 流式请求本轮不 replay，避免缓存 SSE/stream event 造成语义漂移。
- 增加持久化 schema、service 单测和 controller 回归。

## 非目标

- 不实现 SSE/stream replay。
- 不覆盖所有 OpenAI resource create endpoint。
- 不改变上游 OpenAI Direct header 下发策略。
- 不实现记录过期清理任务，后续由横切维护任务承接。

## 输入

- `OpenAiChatCompletionsController`
- `OpenAiResponsesController`
- `CanonicalRequestMetadata.idempotencyKey`
- `GatewayChatExecutionService`
- JPA/Liquibase persistence 基线

## 输出

- `OpenAiIdempotencyRecordEntity`、repository、Liquibase changelog。
- `OpenAiIdempotencyReplayService`。
- Chat/Responses controller replay 集成。
- 单元测试与 WebFlux 回归。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/`
- `src/main/resources/db/changelog/`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `tasks/index.md`

## 依赖

- `TASK-20260515-002` 已完成 `Idempotency-Key` header 捕获。
- `TASK-20260515-004` 已让 stored Chat create 可返回本地资源对象，本任务只缓存最终 HTTP JSON payload。

## 风险

- 请求体 fingerprint 如果不校验，会把不同请求错误重放为旧响应。
- 流式 replay 需要事件存储和 SSE framing，本轮不做，避免伪兼容。
- 幂等记录长期增长需要 TTL/清理策略，不能在本轮混入后台清理复杂度。

## 验收标准

- 同一 distributed key、path、`Idempotency-Key` 和请求体重复提交，只执行一次 gateway execution。
- 同一 key 但请求体不同会被拒绝。
- Chat store=true 的最终 stored Chat payload 可被 replay。
- Responses 非流式 payload 可被 replay。
- Liquibase 与 JPA entity 具备唯一约束，防止重复记录。

## 测试边界

- `OpenAiIdempotencyReplayServiceTests` 覆盖 record/replay/mismatch。
- `OpenAiChatCompletionsControllerTests` 覆盖 Chat 重放不调用 execution。
- `OpenAiResponsesControllerTests` 覆盖 Responses 重放不调用 execution。
- 不跑真实 provider smoke。

## 实现结果

- 新增 `openai_idempotency_record` JPA entity、repository 与 Liquibase `0049` changelog，唯一约束覆盖 `distributed_key_id + request_path + idempotency_key`。
- 新增 `OpenAiIdempotencyReplayService`：
  - blank key 直接旁路；
  - 新响应记录 request fingerprint、HTTP status、response object type 与最终 JSON payload；
  - 重复 key 且请求体一致时返回缓存 JSON；
  - 重复 key 但请求体不同抛出 `IllegalArgumentException`，OpenAI path 会输出 `invalid_request_error`；
  - 数据库唯一约束竞争时回读已存在记录并重新校验 fingerprint。
- `OpenAiChatCompletionsController`：
  - 非流式 `POST /v1/chat/completions` 先查 replay；
  - Chat 普通响应和 `store=true` stored Chat 最终 JSON payload 都会进入 remember；
  - replay 命中时返回 `X-AI-Gateway-Idempotency-Replayed: true`。
- `OpenAiResponsesController`：
  - 非流式 `POST /v1/responses` 先查 replay；
  - Responses 普通响应和 `store=true` stored Response 最终 JSON payload 都会进入 remember；
  - stream=true 本轮不 replay。
- Public docs/OpenAPI 显式登记 `Idempotency-Key` header 与本地 replay 行为。

## 验证结果

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiIdempotencyReplayServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests"`
- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiIdempotencyReplayServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`
- 通过：`git diff --check -- src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/OpenAiIdempotencyRecordEntity.java src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/OpenAiIdempotencyRecordRepository.java src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiIdempotencyReplayService.java src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsController.java src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java src/main/resources/db/changelog/db.changelog-master.yaml src/main/resources/db/changelog/changes/db.changelog-0049-openai-idempotency-record.yaml src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiIdempotencyReplayServiceTests.java src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java src/test/java/com/prodigalgal/xaigateway/docs/PublicOpenApiSnapshotTests.java docs/public-api-compatibility.md docs/openapi/public-openapi.json tasks/done/TASK-20260515-009-openai-idempotency-key-response-replay.md tasks/index.md`

## 遗留问题

- 流式 SSE replay 尚未实现，继续留在 `TASK-20260514-030` 的 streaming event 切片。
- 幂等记录 TTL/清理策略未在本轮实现，后续可归入维护任务或横切 retention 设计。
- 本轮只覆盖 Chat/Responses 非流式 create；其他 OpenAI resource create endpoint 仍需随资源族推进时接入同一 service。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [TASK-20260514-030](../done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
