# REP-20260524-004 不可对应能力假成功审计

日期：2026-05-24  
关联需求：[REQ-20260524-001](../requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)  
关联任务：[TASK-20260524-001-04](../../tasks/in-progress/TASK-20260524-001-04-unsupported-capability-hard-fail.md)

## 背景

用户已明确：公开厂商 API 网关必须以 native API / provider-specific native profile 和可证明无损翻译为成功条件。不可对应、不可无损或非 native 的能力必须直接失败，不能通过 local fake、emulation、degraded success、opaque marker、metadata/header 标记或本地估算对象让下游客户端误判可用。

本报告承接 `TASK-20260524-001-04`，记录 compact 之后继续发现的假成功风险，并把整改范围拆成可独立验证的切片。

## 审计结论

| 能力面 | 当前风险 | 判定 | 整改边界 |
| --- | --- | --- | --- |
| Responses `file_search` | `OpenAiResponsesController#createResponse` 在 mapper 和 Lossless Matrix 前调用 `bindLocalVectorStores`，会本地检索、注入 `Local file_search context` 并移除 hosted `file_search` tool，导致矩阵看不到 `response.hosted_tool.file_search`。 | 必须整改 | 默认 `/v1/responses` 不再把 hosted `file_search` 绑定成本地 RAG 成功；原始 tool 进入 mapper / matrix 后应明确失败。 |
| Responses `input_tokens` | OpenAI Direct passthrough 不可用时，controller fallback 到 `OpenAiResponsesLocalLifecycleService#inputTokens`，返回官方形状 `response.input_tokens` 与本地估算 token 数。 | 必须整改 | OpenAI Direct native route 不可用时返回 `native_input_tokens_required`，不得返回本地估算的官方成功对象。 |
| Responses `compact` | 历史本地 opaque marker 已在第一阶段整改。 | 已整改 | 保持 OpenAI Direct native passthrough；route 不可用返回 `native_compaction_required`。 |
| Vector Store Search | 属于 gateway-local lifecycle / RAG 支撑能力，文档已说明本地文本检索不等价 OpenAI hosted semantic retrieval。 | 可保留但需持续标注 | 不得把 gateway-local search 宣称为 hosted `file_search_call` lifecycle。 |
| Conversations / Stored Responses local lifecycle | 属于 gateway-local lineage 和本地对象生命周期，不等价官方 remote native lifecycle。 | 可保留但需持续标注 | 文档和 SDK 示例需继续突出 local-only 支撑语义。 |
| Media videos / music | 历史本地 media task / provider adapter 会在缺少真实 provider 证据时创建本地任务、推进 `completed` 或合成 `gateway.local` download URL。 | 已整改 | 默认无 native/upstream/profile 时返回 `native_route_required`；GeminiVeo/Suno adapter 缺少真实 `provider_task_id` / artifact URL 时 hard-fail。 |
| Realtime docs | 代码路径已无 `/v1/realtime`，部分历史文档仍可能误导为当前可用。 | 已整改 | 公开文档与 OpenAPI 明确 `/v1/realtime` 为历史归档/当前下线，不再声明当前可用。 |

## Responses 整改方案

- `POST /v1/responses` 默认保留原始 `tools[].type=file_search` 请求体，不再在 controller 前置执行 `OpenAiResponsesFileSearchBindingService#bindLocalVectorStores`。
- hosted `file_search` 在当前 canonical execution 中不是可执行 function tool，也不是可跨协议无损翻译属性；请求应走已有 tool registry / Lossless Matrix hard-fail，错误不应伪装成成功回答。
- `POST /v1/responses/input_tokens` 只允许 OpenAI Direct native passthrough 成功；route 不可用、非 OpenAI Direct 或目标上游无 native 等价能力时返回 OpenAI-style `invalid_request_error`，错误码 `native_input_tokens_required`。
- 历史 `OpenAiResponsesLocalLifecycleService#inputTokens` 本地估算能力已从当前代码中删除；未来如需重建，只能迁到明确 gateway-local 预估 API，并且不得使用官方成功对象让客户端误判。

## Media / Realtime 整改结果

- `GatewayAsyncResourceService#createVideoTask` / `createMusicTask` 默认不再创建 gateway-local media async task；请求没有 `provider_mode=upstream`、`provider_mode=provider` 或 `preferred_credential_id` 时返回 `native_route_required`。
- `GeminiVeoMediaProviderAdapter` 和 `SunoMusicMediaProviderAdapter` 不再自造 provider task id；缺少真实 `provider_task_id` 时直接失败。
- Gemini/Veo 与 Suno completed 状态必须带真实 provider artifact URL；`get` 只读取当前状态，不再把 queued/in_progress 推进到 `completed`。
- media download 只返回真实 provider artifact URL；不会合成 `https://gateway.local/.../download` 产物链接。
- `docs/public-api-compatibility.md` 和 `docs/realtime-provider-websocket.md` 已把 `/v1/realtime` 标记为历史归档 / 当前下线；public OpenAPI 不发布 `/v1/realtime` path，并用 snapshot tests 固定。
- `docs/media-provider-executors.md` 与 media provider matrix 已切换为 native/profile-required 和 hard-fail 口径，避免继续把 generic compatible 或本地 adapter smoke 当作当前默认核心支持。

## 验收标准

- 默认 `POST /v1/responses` 携带 `file_search` 不再触发本地 vector store 检索，也不再返回正常 `response`。
- `POST /v1/responses/input_tokens` 在 OpenAI Direct native route 不可用时返回 HTTP 501 与 `native_input_tokens_required`。
- OpenAI Direct passthrough 成功和上游错误状态保持透明返回。
- public docs / OpenAPI 不再宣称 Responses `file_search` 本地绑定和 `input_tokens` deterministic estimate 是公开 native 成功语义。

## 2026-05-24 残留入口复扫

- `OpenAiResponsesController` 已删除 `OpenAiResponsesLocalLifecycleService` 持有关系与 `OpenAiResponsesFileSearchBindingService` 注入，避免后续误把本地估算或本地 `file_search` 绑定重新接回公开 Responses 路径。
- `OpenAiResponsesLocalLifecycleService` 类已删除，当前代码中不再存在会返回官方形状 `response.input_tokens` / `response.compaction` 的本地 lifecycle 生成器。
- `docs/index.md`、`docs/public-api-compatibility.md`、`src/main/resources/functional-service-api-coverage-matrix.json`、`tasks/index.md` 与 `REQ-20260521-005` 已同步：`/v1/vector_stores*`、`/v1/files*` 仅是 gateway-local 支撑面；Responses hosted `file_search` 只能走 OpenAI Direct/native hosted lifecycle，非 native 返回 `native_hosted_tool_required`。
- 关键词复扫仍命中的 `TASK-20260515-*`、`TASK-20260516-*`、`TASK-20260518-*` 与 `REP-20260514-*` 是历史实现记录；当前事实源已由本报告、`REQ-20260524-001`、`TASK-20260524-001-04`、公开兼容文档和 provider catalog supersede。

## 验证

- Responses / public docs / matrix 合并回归已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.LosslessTranslationMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompilerLosslessMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"
```

- Media / Realtime 切片已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"
```

## 遗留风险

- 旧数据库中若已存在历史 `gateway_local_async_task` media lineage，读取和取消兼容逻辑仍可能保留；本切片只阻断新建假成功路径。
- media 真实 provider 网络 adapter、真实 artifact 存储和真实 smoke key 不在本切片内重建。
- Realtime 历史代码和 done 任务仍作为归档存在；当前公开 API 已不声明 `/v1/realtime` 可用，未来恢复需重新做 native route 与 smoke 验收。
