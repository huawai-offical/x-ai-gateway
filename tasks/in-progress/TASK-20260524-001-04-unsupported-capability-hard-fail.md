# TASK-20260524-001-04 不可对应能力直接失败与假成功清理

状态：In Progress  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260524-001](TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)  
上游来源：[REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)

## 背景

讨论中确认 `/v1/responses/compact` 的本地 opaque marker 不能称为 emulation。公开厂商兼容 API 中，只要能力不可对应或不可无损翻译，就应该直接失败，不能返回官方成功对象。

## 目标

- 审计所有 local emulation、estimate、no-op、fallback、opaque marker、compatibility fallback。
- 将不可复制官方语义的 fake success 改为明确失败。
- 优先处理 Responses compact native route required。
- 建立统一 OpenAI-style / Anthropic-style / Gemini-style 错误模型。

## 非目标

- 不删除合法的 gateway-local 支撑能力。
- 不把本地 estimate 全部视为错误；只处理会误导客户端关键状态的成功返回。
- 不在缺乏测试边界时做大规模破坏性清理。

## 输入

- `OpenAiResponsesController.compactResponse`
- `OpenAiResponsesLocalLifecycleService.compact`
- public docs 和 OpenAPI 中 emulation/fallback 说明。
- file_search、Vector Store Search、Realtime、media、本地 lifecycle 边界。

## 输出

- 假成功风险清单。
- compact 非 native route 失败实现。
- 统一 unsupported/native-required 错误码。
- 对其它 API 的保留或整改依据。

## 影响范围

- Responses compact。
- 资源执行入口 blocked plan 拦截。
- public API compatibility docs。
- public OpenAPI snapshot。
- controller tests、docs tests。

## 依赖

- 无损翻译矩阵。
- native adapter 最小契约。

## 风险

- 改为失败可能影响依赖旧 fallback 的客户端，但失败比假成功安全。
- 错误模型若不统一，会降低 SDK 可解释性。

## 验收标准

- `/v1/responses/compact` 非 OpenAI Direct native route 不再返回 `response.compaction`。
- 不可对应能力有明确错误码。
- 测试覆盖 native passthrough 成功、上游错误保留、本地不可用失败。

## 测试边界

- `OpenAiResponsesControllerTests`
- `GatewayOpenAiPassthroughServiceTests`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`

## 当前状态

- 2026-05-24：进入实施。本轮优先处理 `/v1/responses/compact` 非 native route 的 fake success：route 不可用或非 OpenAI Direct 时必须返回明确错误，不再返回本地 `response.compaction` opaque marker；全量 API 审计继续保留在本任务后续状态中。
- 2026-05-24：已完成 `Responses compact` 第一刀整改：`OpenAiResponsesController.compactResponse` 在 OpenAI Direct native route 不可用时返回 HTTP 501 与 OpenAI-style `invalid_request_error`，错误码 `native_compaction_required`；`OpenAiResponsesLocalLifecycleService.compact` 本地 opaque marker 生成器已删除。
- 2026-05-24：已同步 `PublicDocsBundleService`、`docs/public-api-compatibility.md` 与 `docs/openapi/public-openapi.json`，不再宣传 compact local emulation / opaque marker fallback。
- 2026-05-24：验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayOpenAiPassthroughServiceTests"`。
- 2026-05-24：已补资源执行入口硬失败防线：`GatewayResourceExecutionService` 对 `TranslationExecutionPlanCompiler` 产出的不可执行 plan 统一拒绝执行；JSON、binary、multipart 路径都会在调用上游 credential/executor 前失败，并记录 lifecycle failure，不把 `native_required` / `unsupported` 这类逻辑阻断当成上游凭证失败或 cooldown。
- 2026-05-24：验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests"`。
- 2026-05-24：历史 done 任务中关于 compact fallback 的描述属于旧实现记录，本任务已在当前代码和公开文档中 supersede；后续仍需继续审计 file_search、Vector Store Search、Realtime、media 等其它可能 fake success 边界。
