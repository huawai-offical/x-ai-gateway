# REP-20260514 OpenAI API 全量覆盖任务拆解

## 总体判断

要“完全彻底覆盖 OpenAI API”，需要按官方 API Reference 做平台级兼容，而不是只补 Chat/Responses 参数。完整覆盖至少包含四层：

1. 资源族覆盖：所有官方资源族都要有 `Supported`、`Partial`、`Missing`、`Out of scope` 状态。
2. 参数兼容：每个 endpoint 的 body/query/path/header 参数需要全量保真、显式转换或明确拒绝。
3. 对象与事件语义：对象生命周期、streaming event、webhook event、realtime event、pagination、delete/cancel/pause/resume 等状态流转必须一致。
4. 证明体系：public OpenAPI、provider catalog、conformance matrix、SDK examples、真实 smoke 和权限边界必须一致。

## 官方覆盖面

本轮按 OpenAI API Reference 当前导航拆分任务。重点包括：Responses、Chat Completions、Conversations、Webhooks、Audio、Images、Videos、Embeddings、Evals、Fine-tuning、Batches、Files、Uploads、Models、Moderations、Vector Stores、Containers、Skills、Realtime、Administration、Completions、Assistants/Threads/Runs 等。

官方来源：

- https://developers.openai.com/api/reference/overview
- https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create
- https://developers.openai.com/api/reference/resources/responses/methods/create

## 任务层级

| 层级 | 任务 | 目的 |
| --- | --- | --- |
| 父任务 | `TASK-20260514-016` | OpenAI 全量覆盖总控、验收基线和分批推进 |
| P0 | `017`、`018`、`019`、`030`、`031` | 解决最影响客户兼容的 Chat/Responses/Realtime/认证与真实 smoke |
| P1 | `020`、`021`、`022`、`023`、`024` | 补齐官方核心资源生命周期与工具资源 |
| P2 | `025`、`026`、`027`、`028` | 补齐 Videos、Evals、Skills、Administration 与 Legacy/Beta |
| P3 | `029` | 统一公开文档、OpenAPI、catalog、conformance 与客户展示 |

## 子任务清单

| 任务 | 优先级 | 范围 | 依赖 |
| --- | --- | --- | --- |
| `TASK-20260514-017` Chat Completions 全参数与对象生命周期 | Critical | create/list/get/update/delete messages 参数、stream event、response_format/tools | `013` |
| `TASK-20260514-018` Responses 原生执行器与 Responses lifecycle | Critical | create/get/delete/cancel/input_items/count/compact、原生 event schema | `013` |
| `TASK-20260514-019` Conversations、Items、Webhooks 与 Responses 工具生态 | High | conversations/items/webhooks、built-in tools、MCP/custom tools | `018` |
| `TASK-20260514-020` Audio/Images/Embeddings/Moderations 参数 parity | High | multipart/JSON 参数、模型默认值、响应类型 | `014` |
| `TASK-20260514-021` Files/Uploads/Batches/Models 对象生命周期 | High | list/filter/pagination/delete/cancel、models delete | `014` |
| `TASK-20260514-022` Fine-tuning 全生命周期 | High | pause/resume/permissions/graders；events/checkpoints local lineage 已由 `TASK-20260516-013` 补齐 | `021` |
| `TASK-20260514-023` Vector Stores 全栈兼容 | High | stores/files/file_batches/search、polling、pagination；本地 file content read 已由 `TASK-20260518-001` 补齐，本地 text search 已由 `TASK-20260518-002` 补齐，Responses file_search 本地绑定已由 `TASK-20260518-003` 补齐 | `021` |
| `TASK-20260514-024` Containers 与 Code Interpreter 文件 | High | containers、container files、tool resource lineage | `018`、`023` |
| `TASK-20260514-025` Videos API 兼容面 | Medium | video generation、remix、edit、content、delete | `014` |
| `TASK-20260514-026` Evals/Graders/Runs API | Medium | evals、runs、output items、graders | `022` |
| `TASK-20260514-027` Skills API 与工具分发 | Medium | skills metadata、routing、tool binding | `019` |
| `TASK-20260514-028` Administration API 权限隔离与只读优先 | Medium | org/project/users/keys/usage/costs/audit/certs | 独立权限设计 |
| `TASK-20260514-029` OpenAPI/Catalog/Conformance/SDK 事实源统一 | Medium | public OpenAPI、catalog、docs、SDK examples | `015` |
| `TASK-20260514-030` OpenAI 横切协议兼容 | Critical | headers、idempotency、errors、pagination、rate limit、webhook signature | 所有运行时 |
| `TASK-20260514-031` 真实 Smoke 与认证成本防护 | Critical | key vault、record/replay、skipped reason、成本预算 | 所有 API 面 |

## 既有任务处理

- `TASK-20260514-013` 继续作为 Chat/Responses 参数保真的上游包任务，细分到 `017`、`018`、`019`、`030`。
- `TASK-20260514-014` 继续作为官方资源族覆盖差距的上游包任务，细分到 `020` 至 `028`。
- `TASK-20260514-015` 继续作为公开事实源校准的上游包任务，细分到 `029` 与 `031` 的公开验证部分。

## 推进顺序建议

1. 先做 `030` 和 `031`，建立横切兼容与真实 smoke 证据框架。
2. 再做 `017`、`018`，因为 Chat/Responses 是客户最常用且最容易暴露参数兼容问题的入口。
3. 接着做 `021`、`022`、`023`，补齐对象生命周期和文件/向量/微调链路。
4. 最后推进 `019`、`020`、`024` 至 `029`，把工具生态、多模态、管理与公开文档补齐。

## 2026-05-15 推进记录

- 已闭环 `TASK-20260515-001`，作为 `TASK-20260514-030` 的首个横切切片：OpenAI 协议路径错误体改为 OpenAI-style `error` envelope，响应头统一写回 `X-Request-Id` 与 `X-Trace-Id`。
- 非 OpenAI 协议路径保持 gateway 自有 `ApiErrorResponse`，其中 `/v1/messages` 明确作为 Anthropic-compatible 入口保留顶层 `code/message/traceId`。
- 已闭环 `TASK-20260515-002`：`Chat Completions` 与 `Responses` 捕获 `OpenAI-Organization`、`OpenAI-Project`、`Idempotency-Key` 到 canonical metadata，native OpenAI runtime 仅对 `OPENAI_DIRECT` 下发这些官方 headers，避免泄露给 OpenAI-compatible/Grok/Azure 等非 OpenAI Direct 站点。
- 已闭环 `TASK-20260515-003`：Chat create 参数基线补齐 `store`、`metadata`、penalties、`logit_bias`、`logprobs/top_logprobs`、`max_completion_tokens`、`n`、`seed`、`service_tier`、`stop`、`top_p`、`user`、`verbosity`、`safety_identifier` 等字段的 DTO 接收、canonical 保真和 native runtime 下发。
- 已闭环 `TASK-20260515-004`：Chat `store=true` 保存为本地 `chatcmpl_` 资源，并补齐 stored Chat 的 list/get/update/delete/messages 入口、metadata update、软删除、基线 `after/limit/order/model/metadata` 过滤和 controller/service 回归。
- 已闭环 `TASK-20260515-005`：Chat legacy `functions/function_call` 转换到 canonical tools/tool_choice，OpenAI Direct 不再重复下发 raw legacy 字段，OpenAI-compatible 仍保留 raw legacy fallback，并修正 Spring AI `FunctionTool` name/description 参数顺序。
- 已闭环 `TASK-20260515-006`：Chat `response_format` 映射到 Spring AI `ResponseFormat` 强类型字段，覆盖 `text/json_object/json_schema`、schema/name/strict 保真、非法 type OpenAI-style 错误，以及 `extraBody` 重复字段清理。
- 已闭环 `TASK-20260515-007`：Chat `modalities`、`audio`、`web_search_options` 映射到 Spring AI typed request 字段，覆盖 audio voice/format、web search context/location、非法 enum/shape OpenAI-style 错误，以及 `extraBody` 重复字段清理。
- 已闭环 `TASK-20260515-008`：新增 Chat 参数级 parity matrix，更新 public OpenAPI/runtime docs bundle/SDK advanced example，并用 `OpenAiChatParameterEvidenceTests` 锁定 `response_format`、`tools/tool_choice`、`store/metadata`、`modalities/audio`、`web_search_options` 等关键字段的公开证明。
- 已闭环 `TASK-20260515-009`：非流式 Chat/Responses create 已支持 `Idempotency-Key` 本地响应持久化与重放，持久层唯一约束覆盖 key scope，请求体 fingerprint 不一致时拒绝，并在 public OpenAPI/docs bundle 中公开 header 契约。
- 已闭环 `TASK-20260515-010`：补齐 OpenAI stored Chat list/messages 的 `after`、`limit`、`order`、`model`、`metadata[key]` query 参数契约，统一默认 `order=asc`、`limit=20`、范围 1 到 100 与 list envelope 公开证明。
- 已闭环 `TASK-20260515-011`：补齐 OpenAI path 本地限流命中时的 HTTP 429、`rate_limit_error` 与基础 `Retry-After`/`x-ratelimit-*` headers，先不重写治理快照传递链路。
- 已闭环 `TASK-20260515-012`：为 `openai_idempotency_record` 增加默认 24 小时 retention、手动 purge 方法与 scheduled cleanup，避免长期测试记录无界增长。
- 已闭环 `TASK-20260515-013`：按 OpenAI Webhooks 与 Standard Webhooks 规范补齐 `webhook-id`、`webhook-timestamp`、`webhook-signature` 验签和 replay marker 基线，后续 Webhooks controller 直接复用。
- 已闭环 `TASK-20260515-014`：补齐 Chat `stream_options.include_usage` 终块、同一 stream 内稳定 chunk id/created，以及 Responses streaming event 的 `sequence_number`。
- 已闭环 `TASK-20260515-015`：把 OpenAI protocol path matcher 从 `GlobalApiExceptionHandler` 私有方法中提取并增加正/反向路径矩阵，防止新增 endpoint 漏掉 OpenAI-style error envelope；同时补齐 `/v1/videos` 根路径匹配。
- 已通过 `GlobalApiExceptionHandlerTests`，并联动回归 `OpenAiChatCompletionsControllerTests`、`OpenAiResponsesControllerTests`、`OpenAiModelsControllerTests`、`OpenAiNativeGatewayChatRuntimeTests`、`GatewayAsyncResourceStoredChatTests`、`ProviderExecutionSupportServiceTests`；前序横切回归也已覆盖 `AnthropicMessagesControllerTests` 与 `GeminiGenerateContentControllerTests`。
- `TASK-20260514-030` 的 Responses 原生上游 SSE 透明转发已由 `TASK-20260515-023` 闭环，Realtime WebSocket 入口与 session event 基线已由 `TASK-20260516-008` 闭环；WebRTC/SIP/Realtime calls/translation/transcription 与真实二进制帧透传仍按资源族后续任务推进。
- `TASK-20260514-017` 剩余 Chat 专项包括真实 smoke 证据，以及 stored Chat pagination/metadata filter 的数据库级优化。
- 已闭环 `TASK-20260515-016`：作为 `TASK-20260514-031` 的首个真实 smoke 切片，Codex App API responses smoke 已输出标准 `PASS/FAIL/SKIPPED/UNSUPPORTED/NO_PERMISSION/BUDGET_BLOCKED` 分类，usage probe 在额度、速率和权限阻断时不继续发起真实 POST，账号池详情页已展示分类与跳过原因；OpenAI Direct 资源族 smoke 分类骨架后续已由 `TASK-20260516-006` 闭环。
- 已闭环 `TASK-20260515-017`：作为 `TASK-20260514-018` 的 create 基线切片，OpenAI Direct 的非流式 `/v1/responses` 已走原生 Responses HTTP JSON POST，不再经由 Spring AI `ChatCompletionRequest`；上游 Responses 原始字段、官方 headers 与 usage 映射已有本地 HTTP server 回归，streaming SSE 透明转发和 lifecycle endpoints 继续保留为后续切片。
- 已闭环 `TASK-20260515-018`：作为 `TASK-20260514-018` 的本地 lifecycle 切片，stored Responses 已补齐 `POST /v1/responses/{id}/cancel` 与 `GET /v1/responses/{id}/input_items`，覆盖状态流转、input item list envelope、分页参数、公开文档和 OpenAPI snapshot。
- 已闭环 `TASK-20260515-019`：作为 `TASK-20260514-018` 的 streaming 参数切片，Responses stream delta events 已支持 `stream_options.include_obfuscation` 默认开启与显式关闭，覆盖 text、reasoning summary 与 function call arguments delta，并同步公开 docs bundle 与 OpenAPI snapshot。
- 已闭环 `TASK-20260515-020`：作为 `TASK-20260514-018` 的 input accounting / compaction 切片，`POST /v1/responses/input_tokens` 与 `POST /v1/responses/compact` 已建立本地 deterministic estimate / emulation 基线，并同步公开 docs bundle、OpenAPI snapshot 与兼容文档；`responses/input_tokens` native passthrough 后续已由 `TASK-20260516-002` 闭环，native compact passthrough 后续已由 `TASK-20260516-003` 闭环。
- 已闭环 `TASK-20260515-021`：作为 `TASK-20260514-018` 的 lifecycle query 参数切片，stored Response retrieve/input_items 已接收 `include` query 参数，并同步公开 OpenAPI 与本地 no-op 兼容说明；远端 include 语义仍保留给 native passthrough 切片。
- 已闭环 `TASK-20260515-022`：作为 `TASK-20260514-018` 的 raw object 双轨切片，OpenAI Direct 非流式 Responses create 的上游原始 JSON 已可穿透 runtime 与 controller 输出，并在返回前重写 `model` 为 public model；streaming raw SSE 后续已由 `TASK-20260515-023` 闭环，远端 lifecycle passthrough 后续已由 `TASK-20260516-001` 闭环。
- 已闭环 `TASK-20260515-023`：作为 `TASK-20260514-018` 的 streaming raw 切片，OpenAI Direct `/v1/responses` `stream=true` 已建立上游 SSE 原始事件透明转发路径，保留 event/data/sequence/未知字段；非 OpenAI Direct 与本地 fallback 继续保留 canonical stream encoder。
- 已闭环 `TASK-20260516-001`：作为 `TASK-20260514-018` 的远端 lifecycle 切片，OpenAI Direct native create + `store=true` 会记录 upstream Response lineage；带 lineage 的 stored Response 已支持远端 retrieve/delete/cancel/input_items passthrough，并在返回前保持本地 `resp_...` id。
- 已闭环 `TASK-20260516-002`：作为 `TASK-20260514-018` 的 input accounting native 切片，OpenAI Direct `/v1/responses/input_tokens` 已优先转发到上游精确计数；route 不可用或非 OpenAI Direct 时保留本地 deterministic estimate fallback，上游已执行后的错误状态不被吞掉。
- 已闭环 `TASK-20260516-003`：作为 `TASK-20260514-018` 的 compaction native 切片，OpenAI Direct `/v1/responses/compact` 已优先转发到上游真实 compaction；route 不可用或非 OpenAI Direct 时保留本地 opaque marker emulation fallback，上游已执行后的错误状态不被吞掉。
- 已闭环 `TASK-20260517-002`：作为 `TASK-20260514-018` 的无 lineage 远端 lifecycle 切片，未知远端 `resp_...` id 在本地无 lineage 时，可通过 `model` query 或 `X-AI-Gateway-OpenAI-Model` header 显式路由到 OpenAI Direct retrieve/delete/cancel/input_items passthrough；无 hint 时继续本地 not found，不做凭证猜测。
- 已闭环 `TASK-20260516-004`：作为 `TASK-20260514-017` 与 `TASK-20260514-030` 的 stored Chat pagination 硬化切片，`GET /v1/chat/completions` 已改为专用数据库游标查询，下推租户、类型前缀、model、createdAt/id cursor 与排序；metadata 继续做 JSON 精确过滤并跨批次扫描，避免固定 scan window 漏页。
- 已闭环 `TASK-20260516-005`：作为 `TASK-20260514-031` 的 OpenAI Direct key vault 与权限探测切片，后台 credential smoke 已可按 `credentialId` 引用加密 `OPENAI_DIRECT` key；dry-run 不解密、不访问上游，live probe 仅执行低成本 `GET /v1/models`，并统一输出 PASS/FAIL/SKIPPED/UNSUPPORTED/NO_PERMISSION/BUDGET_BLOCKED 分类与脱敏 request preview。
- 已闭环 `TASK-20260516-006`：作为 `TASK-20260514-031` 的资源族 smoke runner 分类骨架切片，后台可按同一 `credentialId` 输出 Chat、Responses、Files、Batches、Vector Stores、Realtime client secret 六类 item 与 summary；live 默认只执行 Files/Batches/Vector Stores 只读 list probe，Chat/Responses billable generation 与 Realtime client secret 写操作会按预算/写操作保护输出 `BUDGET_BLOCKED`。
- 已闭环 `TASK-20260516-007`：作为 `TASK-20260514-031` 的 certification 与脱敏 fixture 基线切片，后台可生成资源族 smoke certification report、fixtureSnapshots 与顶层 `DRY_RUN/CERTIFIED/PARTIAL_CERTIFIED/NO_PERMISSION/BUDGET_BLOCKED/UNSUPPORTED/FAILED` 状态；live certification 会把脱敏摘要写入 credential metadata，dry-run 不写入。
- 已闭环 `TASK-20260516-008`：作为 `TASK-20260514-030` 的 Realtime 横切切片，`/v1/realtime?model=...` 已提供 OpenAI-compatible WebSocket handler，复用 Distributed Key 鉴权和 `openai_realtime` Live Session，首个 outbound event 为 `session.created`，`session.update` 返回 `session.updated`，非法 JSON/缺失 type/非文本帧返回 OpenAI-style `error`。
- 已闭环 `TASK-20260516-009`：作为 `TASK-20260514-031` 的受控真实 smoke 切片，Chat/Responses billable generation probe 与 Realtime client secret 写操作 probe 已增加显式 allow flags；未开启时保持预算/写操作阻断，开启时分别使用 `max_completion_tokens=1`、`max_output_tokens=1`、短 TTL text-only realtime session 的最小 payload。
- 已闭环 `TASK-20260516-010`：作为 `TASK-20260514-015/029` 的事实源校准切片，OpenAI Direct provider catalog 不再使用空 `unsupportedFeatures`；catalog 已明确列出 Conversations、Vector Stores full stack、Fine-tuning events/checkpoints、Models delete、Containers、Evals、Administration 与 Realtime full calls/WebRTC/SIP 等当时未完成边界，并把 Chat/Responses/Realtime/smoke 近期闭环项纳入 `conformanceChecks`。其中 Models delete 的 gateway registry 边界后续已由 `TASK-20260516-012` 闭环，Conversations local lifecycle 后续已由 `TASK-20260516-015` 闭环，上游 owner-role passthrough 仍保留为未完成声明。
- 已闭环 `TASK-20260516-011`：作为 `TASK-20260514-021/015` 的 Batches list 切片，`GET /v1/batches` 已返回当前 DistributedKey 下经 gateway 创建并持久化的 Batch lineage list envelope，并同步回收 `/v1/batches [GET]` accepted exception、provider catalog、public docs 与 OpenAPI。
- 已闭环 `TASK-20260516-012`：作为 `TASK-20260514-021/015` 的 Models delete 切片，`DELETE /v1/models/{model}` 已暴露为 gateway-registered fine-tuned model registry delete；仅匹配当前 DistributedKey 下 tuning lineage 的 `registered_model_key`、`registered_model_name` 或自动 alias，拒绝公共模型和跨租户模型，并同步 provider catalog/public docs/OpenAPI。
- 已闭环 `TASK-20260516-013`：作为 `TASK-20260514-022/014/015` 的 Fine-tuning events/checkpoints 切片，`GET /v1/fine_tuning/jobs/{jobId}/events` 与 `GET /v1/fine_tuning/jobs/{jobId}/checkpoints` 已返回当前 DistributedKey 下 gateway-tracked tuning job 的本地 lineage list，并同步回收对应 accepted exception、provider catalog、public docs 与 OpenAPI。
- 已闭环 `TASK-20260516-014`：作为 `TASK-20260514-019/013` 的 Responses tools 边界切片，`function` tools 保持 canonical 执行，`web_search_preview`、`file_search`、`mcp`、`custom`、`code_interpreter`、`computer_use_preview`、`image_generation`、`shell/apply_patch` 等非 function tools 与对应 `tool_choice` 会显式返回 OpenAI-style 错误，不再静默跳过；provider catalog、public docs 与 OpenAPI 已同步 `openai.responses-tool-registry-boundary`。
- 已闭环 `TASK-20260516-015`：作为 `TASK-20260514-019` 的 Conversations 切片，`/v1/conversations` 与 `/v1/conversations/{conversationId}/items` 已建立 gateway local lineage lifecycle，支持 conversation create/retrieve/update/delete、item create/list/retrieve/delete、`after/include/limit/order` query 和一次最多 20 条 item 的批量边界；provider catalog、public docs 与 OpenAPI 已同步 `openai.conversations-local-lifecycle`。
- 已闭环 `TASK-20260516-016`：作为 `TASK-20260514-019/030` 的 Webhooks ingress 切片，`POST /v1/webhooks/openai` 已复用 Standard Webhooks signature/replay verifier，按 raw body 验签，把合法 event 保存为 `WEBHOOK_EVENT`，并对重复 delivery 或重复 event id 返回 `duplicate=true` 且不二次落库；provider catalog、public docs 与 OpenAPI 已同步 `openai.webhooks-ingress-event-persistence`。
- 已闭环 `TASK-20260516-017`：作为 `TASK-20260514-031` 的 record/replay fixture 固化切片，OpenAI Direct certification response 与 live metadata 已包含版本化 `recordReplayFixture`，仓库 sample fixture 锁定 schema、脱敏策略和 replay policy；真实网络、billable 与 write operation 默认只允许 replay，不会因 fixture 存在自动访问上游。
- 已闭环 `TASK-20260517-001`：作为 `TASK-20260514-031` 的 CI replay 校验切片，新增离线 `OpenAiDirectSmokeRecordReplayFixtureVerifier`，可在无网络、无真实 key 环境校验 record/replay fixture 的 schema、replay-only policy、summary 计数、fixture 必填字段和敏感信息脱敏；provider catalog 已同步 `openai.direct-smoke-record-replay-ci-verifier`。
- 已闭环 `TASK-20260517-003`：作为 `TASK-20260514-023` 的 Vector Stores 本地 lifecycle 基线切片，`POST/GET /v1/vector_stores` 与 `GET/POST/DELETE /v1/vector_stores/{vectorStoreId}` 已支持 create/list/retrieve/update/delete，使用 `gateway_async_resource` 的 `VECTOR_STORE` 做 Distributed Key 隔离和软删除；files、file_batches、file content、本地 text search 和 Responses `file_search` 本地绑定后续已分别闭环，真实向量检索与 hosted `file_search_call` lifecycle 仍保留为后续切片。
- 已闭环 `TASK-20260517-004`：作为 `TASK-20260514-023` 的 Vector Store Files 本地 attachment lifecycle 切片，`POST/GET /v1/vector_stores/{vectorStoreId}/files` 与 `GET/DELETE /v1/vector_stores/{vectorStoreId}/files/{fileId}` 已支持 attach/list/retrieve/delete，使用 `VECTOR_STORE_FILE` child resource 保留同 file id 跨 vector store 复用能力，并在 create/delete 时同步 parent `file_counts`；file content 已由 `TASK-20260518-001` 补齐为本地读取基线，本地 text search 已由 `TASK-20260518-002` 补齐。
- 已闭环 `TASK-20260517-005`：作为 `TASK-20260514-023` 的 Vector Store File Batches 本地 lifecycle 切片，`POST /v1/vector_stores/{vectorStoreId}/file_batches`、`GET /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}`、`POST /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/cancel` 与 `GET /v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files` 已支持本地 batch create/retrieve/cancel/list files；批量 create 先做非空、去重和已存在 attachment 校验，成功后创建 `VECTOR_STORE_FILE_BATCH` 与多个 `VECTOR_STORE_FILE` attachment，并同步 parent `file_counts`。file content 已由 `TASK-20260518-001` 补齐，本地 text search 已由 `TASK-20260518-002` 补齐，Responses `file_search` 本地绑定已由 `TASK-20260518-003` 补齐；真实向量检索与 hosted `file_search_call` lifecycle 仍保留为后续切片。
- 已闭环 `TASK-20260518-001`：作为 `TASK-20260514-023` 的 Vector Store File Content 本地读取切片，`GET /v1/vector_stores/{vectorStoreId}/files/{fileId}/content` 已按当前 Distributed Key、parent vector store 与 active attachment 校验读取 gateway file，并返回 `vector_store.file_content.page` 文本页；该能力只声明本地 UTF-8 文本读取，不等价于 OpenAI 托管解析、embedding 或真实向量索引。本地 text search 后续已由 `TASK-20260518-002` 补齐。
- 已闭环 `TASK-20260518-002`：作为 `TASK-20260514-023` 的 Vector Store Search 本地文本检索切片，`POST /v1/vector_stores/{vectorStoreId}/search` 已按当前 Distributed Key、parent vector store、active attachment、attributes filter、`max_num_results` 与 `ranking_options.score_threshold` 搜索可读取 gateway file 文本，并返回 `vector_store.search_results.page`；该能力只声明 deterministic UTF-8 lexical baseline，不等价于 OpenAI 托管语义向量检索、rerank 或 query rewrite。
- 已闭环 `TASK-20260518-003`：作为 `TASK-20260514-023` 的 Responses File Search 本地 Vector Store 绑定切片，`/v1/responses` 已可接收 `tools[].type=file_search`，校验本地 `vector_store_ids`，复用本地 Vector Store Search 结果注入 `Local file_search context`，并在 canonical mapper/provider 前移除 hosted tool；强制 `tool_choice.type=file_search` 与 allowed_tools 限定 file_search 会被拒绝，hosted `file_search_call` lifecycle 仍保留为后续切片。
