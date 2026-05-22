# TASK-20260515-010 OpenAI List Pagination Envelope 与 Cursor 参数基线

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-030](../done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[TASK-20260515-004](../done/TASK-20260515-004-openai-stored-chat-lifecycle-foundation.md)

## 背景

stored Chat Completions 生命周期已经具备 list/get/update/delete/messages 基线，但分页语义仍主要散落在 controller 与 service 内部：`limit` 非法值会被静默夹紧，Chat Completion list 的默认排序跟官方 `asc` 默认值不一致，公开 OpenAPI 也没有登记 `after`、`limit`、`order`、`model` 与 `metadata[key]` 查询参数。继续推进 OpenAI 横切兼容时，需要先把 list envelope 与 cursor 参数固定为可测试、可公开的契约。

## 目标

- 对齐官方 stored Chat list/messages 分页参数：`after` cursor、`limit` 默认 20、`limit` 范围 1 到 100、`order` 仅允许 `asc` 或 `desc` 且默认 `asc`。
- 统一 `/v1/chat/completions` 与 `/v1/chat/completions/{completionId}/messages` 的分页响应 envelope：`object=data list`、`data`、`first_id`、`last_id`、`has_more`。
- 非法 `limit`、非法 `order` 在 OpenAI path 返回 OpenAI-style `invalid_request_error`，不再静默夹紧。
- 公开 OpenAPI/runtime docs bundle 和本地 Markdown 文档显式登记 list query 参数与响应形态。
- 增加 controller/service/docs 回归，锁住默认排序、边界值和公开契约。

## 非目标

- 不在本轮实现数据库级 cursor 查询优化，现有 repository 采样 + 内存过滤保留为基线。
- 不扩展到 Files、Batches、Fine-tuning、Vector Stores 等其他 OpenAI list endpoint；这些 endpoint 后续随对应资源族任务接入同一分页口径。
- 不实现 `before` cursor；stored Chat list/messages 官方当前公开参数只需要 `after`。

## 输入

- OpenAI Chat Completions API Reference stored list/messages 参数说明。
- `OpenAiChatCompletionsController`
- `GatewayAsyncResourceService`
- `PublicDocsBundleService`
- `docs/openapi/public-openapi.json`

## 输出

- OpenAI list pagination 参数归一化与校验。
- stored Chat list/messages 默认排序与 envelope 回归。
- Public OpenAPI/docs bundle 查询参数与响应说明。
- 本地任务与上游报告回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsController.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceService.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceStoredChatTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java`
- `src/test/java/com/prodigalgal/xaigateway/docs/PublicOpenApiSnapshotTests.java`
- `docs/public-api-compatibility.md`
- `docs/openapi/public-openapi.json`
- `tasks/index.md`

## 依赖

- `TASK-20260515-004` 已提供 stored Chat lifecycle endpoint。
- `TASK-20260515-001` 已保证 OpenAI path 的错误 envelope。
- `TASK-20260515-008` 已建立 public OpenAPI/docs bundle 快照测试。

## 风险

- 将 Chat Completion list 默认排序改为 `asc` 可能改变旧客户端看到的默认返回顺序，需要通过文档说明这是 OpenAI 兼容修正。
- 现阶段仍是 repository 采样后内存过滤，大数据量下 `has_more` 可能受 scan window 影响；本轮明确保留为后续数据库级优化。
- 静默夹紧改为非法参数报错后，依赖错误输入的脚本会更早失败，但这更接近官方 SDK 预期。

## 验收标准

- `GET /v1/chat/completions` 默认 `order=asc`，显式 `order=desc` 可反向排序。
- `GET /v1/chat/completions/{completionId}/messages` 默认 `order=asc`，显式 `order=desc` 可反向排序。
- `limit` 为空时默认 20；`limit < 1`、`limit > 100` 或非整数返回 OpenAI-style `invalid_request_error`。
- list 响应始终包含 `object`、`data`、`has_more`，非空页包含 `first_id` 与 `last_id`。
- public OpenAPI/runtime docs bundle 暴露 stored Chat list/messages query 参数。

## 测试边界

- `GatewayAsyncResourceStoredChatTests` 覆盖默认升序、降序、cursor、limit envelope。
- `OpenAiChatCompletionsControllerTests` 覆盖非法 `limit/order` 错误与 query 参数下发。
- `PublicDocsBundleServiceTests` 与 `PublicOpenApiSnapshotTests` 覆盖 OpenAPI query 参数。
- 不跑真实 OpenAI smoke。

## 当前设计

- 在 OpenAI controller 入口做参数校验，避免 service 层继续接收越界 `limit`。
- service 层仍保留 defensive clamp/default，防止内部调用绕过 controller 造成空页或超大页。
- Chat Completion list 默认按 repository `createdAt desc` 结果反转为 `asc`；显式 `desc` 保持 repository 顺序。
- Message list 保留请求体顺序作为 `asc`，显式 `desc` 反转。

## 实现结果

- `OpenAiChatCompletionsController`：
  - `/v1/chat/completions` 与 `/v1/chat/completions/{completionId}/messages` 统一校验 `limit` 与 `order`；
  - `limit` 仅允许 1 到 100，非整数会返回 OpenAI-style `invalid_request_error`；
  - `order` 仅允许 `asc` 或 `desc`，为空交给 service 使用默认 `asc`。
- `GatewayAsyncResourceService`：
  - stored Chat list 默认 `limit=20`、`order=asc`；
  - Chat Completion list 对 repository `createdAt desc` 采样结果反转为默认升序；
  - Message list 保留请求体顺序为默认升序，显式 `desc` 反转；
  - service 层继续做 defensive 校验，防止内部调用绕过 controller。
- `PublicDocsBundleService` 与 `docs/openapi/public-openapi.json`：
  - `/v1/chat/completions` `GET` 增加 `after`、`limit`、`model`、`order`、`metadata[key]` query 参数；
  - `/v1/chat/completions/{completionId}/messages` `GET` 增加 `completionId` path 参数和 `after`、`limit`、`order` query 参数；
  - docs bundle 增加 `openai.list-pagination-envelope` conformance check。
- `docs/public-api-compatibility.md` 增加 OpenAI Stored Chat Pagination 公开说明。

## 验证结果

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceStoredChatTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`
- 通过：`git diff --check -- src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsController.java src/main/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceService.java src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java src/test/java/com/prodigalgal/xaigateway/gateway/core/resource/GatewayAsyncResourceStoredChatTests.java src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java src/test/java/com/prodigalgal/xaigateway/docs/PublicOpenApiSnapshotTests.java docs/public-api-compatibility.md docs/openapi/public-openapi.json tasks/index.md docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md tasks/done/TASK-20260515-010-openai-list-pagination-envelope-cursor-baseline.md`

## 遗留问题

- 本轮只闭环 stored Chat list/messages；其他 OpenAI list endpoint 仍需随对应资源族任务接入同一分页口径。
- 数据库级 cursor 查询优化未纳入本轮，仍保留在 `TASK-20260514-017` 的 stored Chat pagination/metadata filter 优化范围。
- `before` cursor 未实现；当前 stored Chat 官方面只要求 `after`。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [TASK-20260514-030](../done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
