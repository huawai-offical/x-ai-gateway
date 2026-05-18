# TASK-20260516-003 OpenAI Responses compact Native Passthrough

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)  
上游来源：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)、[TASK-20260515-020](../done/TASK-20260515-020-openai-responses-input-tokens-compact-baseline.md)、[TASK-20260516-002](../done/TASK-20260516-002-openai-responses-input-tokens-native-passthrough.md)

## 背景

`POST /v1/responses/compact` 已有本地 emulation，可返回 `object=response.compaction`、`output` 和 `usage`，避免 SDK 调用 404。但官方 Responses API 中该 endpoint 会执行真实 compaction pass，并返回 encrypted opaque compaction item；本地 marker 只能作为兼容 fallback，不能等价于 OpenAI Direct native compaction。

## 目标

- 为 `POST /v1/responses/compact` 增加 OpenAI Direct/native passthrough 路径，向上游发送原始 request body。
- 沿用 `GatewayOpenAiPassthroughService.executeOpenAiDirectJson` 的 OpenAI Direct route 约束、resolved model 写入和上游错误保真能力。
- 保留本地 compact emulation fallback，避免没有 OpenAI Direct route 的社区 key 直接失效。
- 只在路由缺失、非 OpenAI Direct route 或 native passthrough 前置条件不满足时 fallback；上游已执行后的 4xx/5xx 必须原样返回。
- 公开文档明确 native compaction 与本地 emulation 的边界。

## 非目标

- 不实现真实本地 compaction 模型或本地加密 compaction item。
- 不改变 `/v1/responses/input_tokens` native passthrough 行为。
- 不改变 stored Response retrieve/delete/cancel/input_items lifecycle。
- 不支持任意未知远端 `resp_...` id 的无 lineage 盲路由。

## 输入

- OpenAI Responses compact API reference：`https://developers.openai.com/api/reference/resources/responses/methods/compact`
- OpenAI Conversation state compaction guidance：`https://developers.openai.com/api/docs/guides/conversation-state`
- `OpenAiResponsesController.compactResponse`
- `OpenAiResponsesLocalLifecycleService.compact`
- `GatewayOpenAiPassthroughService.executeOpenAiDirectJson`

## 输出

- compact native passthrough controller wiring。
- 本地 fallback 边界和 upstream error preservation 测试。
- 公开 docs bundle、OpenAPI snapshot、兼容文档和任务索引更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayOpenAiPassthroughService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayOpenAiPassthroughServiceTests.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java`
- `docs/public-api-compatibility.md`
- `docs/openapi/public-openapi.json`

## 依赖

- `TASK-20260515-020` 的本地 compact baseline。
- `TASK-20260516-002` 引入的 OpenAI Direct native JSON passthrough helper。
- 路由选择、CredentialMaterialResolver、错误规则和官方 header 下发基线。

## 风险

- 如果对上游已执行后的错误也 fallback，会把真实 invalid model、quota、auth、rate limit 等错误伪装成本地 compaction marker。
- OpenAI Direct compact 要求 `model`；本地 fallback 可以继续兼容弱请求，但 native route 仍应遵守现有 route/model resolution 规则。
- 本地 emulation 的 `encrypted_content` 只是 opaque marker，不应在文档中表达为等价官方 encrypted compaction item。

## 验收标准

- 有可用 OpenAI Direct route 时，`POST /v1/responses/compact` 调用上游 `/v1/responses/compact` 并返回上游 `object=response.compaction`、`output`、`usage`。
- 无可用 route 或 route 不是 OpenAI Direct 时，仍返回本地 compact emulation。
- 上游已执行后的 4xx/5xx 保留原 HTTP status 和 body。
- 公开 docs bundle、OpenAPI snapshot 和兼容文档都说明 native passthrough 与 fallback 边界。
- Targeted tests 通过，并回写父任务与任务索引。

## 测试边界

- 不访问真实 OpenAI 远端。
- controller test 使用 mock passthrough service 覆盖 native 成功、fallback、错误不吞。
- passthrough service test 使用 fake WebClient 验证 compact path、method、Authorization 与 request body。
- docs test 覆盖 conformance item 与 OpenAPI path description。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
- [TASK-20260515-020](../done/TASK-20260515-020-openai-responses-input-tokens-compact-baseline.md)
- [TASK-20260516-002](../done/TASK-20260516-002-openai-responses-input-tokens-native-passthrough.md)

## 实现结果

- `OpenAiResponsesController.compactResponse` 改为优先调用 `GatewayOpenAiPassthroughService.executeOpenAiDirectJson("/v1/responses/compact")`。
- OpenAI Direct route 可用时保留上游 `ResponseEntity`、HTTP status 与 `response.compaction` body；route 不可用、非 OpenAI Direct 或本地前置校验失败时回退 `OpenAiResponsesLocalLifecycleService.compact`。
- 上游已执行后的 4xx/5xx 不会被静默改写成本地 marker。
- 公开 docs bundle、OpenAPI snapshot 和兼容文档已标注 native compaction 与本地 emulation fallback 边界。

## 验证情况

- `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayOpenAiPassthroughServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`
- `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayOpenAiPassthroughServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`

## 遗留问题

- 本切片未实现本地真实模型 compaction；非 OpenAI Direct route 仍使用本地 opaque marker emulation。
- 任意未知远端 `resp_...` id 的无 lineage 盲路由仍不支持，继续保留在父任务边界中。
