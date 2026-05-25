# TASK-20260524-001-04 不可对应能力直接失败与假成功清理

状态：Done  
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
- 历史 `OpenAiResponsesLocalLifecycleService` 本地 input_tokens / compact 入口
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
- Media Video/Music async task native/profile route required 与 provider adapter 假成功清理。
- Realtime WebSocket 当前公开能力口径修正。
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
- `GatewayAsyncResourceServiceTests` 的 Video/Music native route required 与 adapter negative tests。
- Realtime 公开文档 snapshot / docs bundle 测试。

## Media / Realtime 子切片 Task Spec

### 背景

历史 media adapter 为了完成 provider-specific task smoke，允许 GeminiVeo/Suno 在没有真实 provider 响应时创建本地任务，并在 `get` 时推进到 `completed` 与本地 download URL。历史 Realtime 文档也仍把 `/v1/realtime` WebSocket 写成当前可用入口。当前 REQ-20260524-001 要求不可对应、不可无损、非 native 能力必须 hard-fail。

### 目标

- Video/Music 默认请求没有 native upstream/profile 时不再创建本地 completed/local artifact 假成功。
- GeminiVeo/Suno adapter 不再在没有真实 provider 响应时自动 completed/download 成功。
- Realtime 公开文档和 OpenAPI 不再宣称 `/v1/realtime` 当前可用。

### 非目标

- 不改 Responses create/file_search/input_tokens 相关 controller/tests。
- 不重建完整 media provider 网络 adapter。
- 不删除历史 done 任务，只在当前文档中标记历史归档与 supersede。

### 上游来源

- [REQ-20260524-001](../../docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)
- [REP-20260524-004](../../docs/reports/REP-20260524-004-unsupported-capability-fake-success-audit.md)

### 输入

- `GatewayAsyncResourceService`
- `GatewayMediaTasksController`
- `GeminiVeoMediaProviderAdapter`
- `SunoMusicMediaProviderAdapter`
- `docs/public-api-compatibility.md`
- `docs/realtime-provider-websocket.md`
- public docs/OpenAPI snapshot tests

### 输出

- Media native route required / provider evidence required hard-fail。
- Adapter negative tests。
- Realtime 下线/历史归档文档修正。
- 验证命令与遗留风险记录。

### 当前状态

- 2026-05-24：进入 Media/Realtime 子切片实施。已识别 GeminiVeo/Suno adapter 本地 completed/download 假成功风险，以及 `/v1/realtime` WebSocket 公开文档误导风险；先补报告，再做最小 hard-fail 与文档修正。
- 2026-05-24：Media/Realtime 子切片已完成。Video/Music 默认无 upstream/native/profile 时返回 `native_route_required`；GeminiVeo/Suno adapter 缺少真实 `provider_task_id` 或真实 artifact URL 时 hard-fail，读取不会自动推进 completed，下载不会合成 `gateway.local` URL；Realtime 文档与 OpenAPI 已标记 `/v1/realtime` 历史归档 / 当前下线。
- 2026-05-24：验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`。

## 当前状态

- 2026-05-24：进入实施。本轮优先处理 `/v1/responses/compact` 非 native route 的 fake success：route 不可用或非 OpenAI Direct 时必须返回明确错误，不再返回本地 `response.compaction` opaque marker；全量 API 审计继续保留在本任务后续状态中。
- 2026-05-24：已完成 `Responses compact` 第一刀整改：`OpenAiResponsesController.compactResponse` 在 OpenAI Direct native route 不可用时返回 HTTP 501 与 OpenAI-style `invalid_request_error`，错误码 `native_compaction_required`；历史 `OpenAiResponsesLocalLifecycleService` 本地 opaque marker / deterministic estimate 入口已从公开 controller 链路移除。
- 2026-05-24：已同步 `PublicDocsBundleService`、`docs/public-api-compatibility.md` 与 `docs/openapi/public-openapi.json`，不再宣传 compact local emulation / opaque marker fallback。
- 2026-05-24：验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayOpenAiPassthroughServiceTests"`。
- 2026-05-24：已补资源执行入口硬失败防线：`GatewayResourceExecutionService` 对 `TranslationExecutionPlanCompiler` 产出的不可执行 plan 统一拒绝执行；JSON、binary、multipart 路径都会在调用上游 credential/executor 前失败，并记录 lifecycle failure，不把 `native_required` / `unsupported` 这类逻辑阻断当成上游凭证失败或 cooldown。
- 2026-05-24：验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests"`。
- 2026-05-24：历史 done 任务中关于 compact fallback 的描述属于旧实现记录，本任务已在当前代码和公开文档中 supersede；后续仍需继续审计 file_search、Vector Store Search、Realtime、media 等其它可能 fake success 边界。
- 2026-05-24：已补充假成功审计报告 [REP-20260524-004](../../docs/reports/REP-20260524-004-unsupported-capability-fake-success-audit.md)。本轮切片聚焦 Responses `file_search` 与 `input_tokens`：`file_search` 不得在 controller 前置本地绑定并移除 hosted tool，`input_tokens` 不得在 native route 不可用时返回本地估算的官方成功对象；media 和 Realtime 文档误导由独立切片继续处理。
- 2026-05-24：Media/Realtime 子切片已启动，关联报告 `REP-20260524-004`。本切片仅处理 media native required/hard-fail 与 realtime 文档误导，不触碰主 agent 正在处理的 Responses create/file_search/input_tokens 范围。
- 2026-05-24：Media/Realtime 子切片已完成并回写报告。遗留风险：旧库中历史 media local lineage 的读取/取消兼容逻辑仍可能存在；真实 media provider 网络 adapter、真实 artifact 存储和 Realtime 重新上线 smoke 不在本切片范围内。
- 2026-05-24：Responses `file_search` / `input_tokens` 子切片已完成。`/v1/responses` 默认不再调用 `OpenAiResponsesFileSearchBindingService#bindLocalVectorStores`，hosted `file_search` 保留为 native-required 属性并由矩阵/执行层返回 `native_hosted_tool_required`；`/v1/responses/input_tokens` 在 OpenAI Direct native route 不可用时返回 HTTP 501 与 `native_input_tokens_required`，不再返回本地 deterministic estimate 官方成功对象。合并验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.LosslessTranslationMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompilerLosslessMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"`。
- 2026-05-24：完成残留入口与当前事实源复扫。`OpenAiResponsesController` 已删除 `OpenAiResponsesLocalLifecycleService` 持有关系与 `OpenAiResponsesFileSearchBindingService` 注入，`OpenAiResponsesLocalLifecycleService` 类已删除；`docs/index.md`、`docs/public-api-compatibility.md`、`functional-service-api-coverage-matrix.json`、`tasks/index.md` 与 `REQ-20260521-005` 已同步当前口径：Vector Stores / Files 仍是 gateway-local 支撑面，Responses hosted `file_search` 只能走 OpenAI Direct/native hosted lifecycle，非 native 返回 `native_hosted_tool_required`。剩余命中为历史报告或 done 任务记录，不作为当前可执行语义。
- 2026-05-24：完成并归档。覆盖 Responses compact/input_tokens/file_search、resource blocked plan、media native route required、Realtime current-down 文档、public docs/OpenAPI/coverage matrix 当前事实源与高风险关键词复扫。验证通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.docs.FunctionalCoverageProviderBoundaryTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.LosslessTranslationMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompilerLosslessMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"`。
