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
| `TASK-20260514-022` Fine-tuning 全生命周期 | High | events/checkpoints/pause/resume/permissions/graders | `021` |
| `TASK-20260514-023` Vector Stores 全栈兼容 | High | stores/files/file_batches/search、polling、pagination | `021` |
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
- `TASK-20260514-030` 剩余横切项仍包括 Realtime WebSocket event 与 Responses 原生上游 SSE 透明转发；新增 endpoint path 清单已由 `OpenAiProtocolPathMatcherTests` 接管。
- `TASK-20260514-017` 剩余 Chat 专项包括真实 smoke 证据，以及 stored Chat pagination/metadata filter 的数据库级优化。
- 已闭环 `TASK-20260515-016`：作为 `TASK-20260514-031` 的首个真实 smoke 切片，Codex App API responses smoke 已输出标准 `PASS/FAIL/SKIPPED/UNSUPPORTED/NO_PERMISSION/BUDGET_BLOCKED` 分类，usage probe 在额度、速率和权限阻断时不继续发起真实 POST，账号池详情页已展示分类与跳过原因；Files/Batches/Vector Stores/Realtime client secret 等资源族 smoke 仍保留在 `TASK-20260514-031` 后续切片中。
- 已闭环 `TASK-20260515-017`：作为 `TASK-20260514-018` 的 create 基线切片，OpenAI Direct 的非流式 `/v1/responses` 已走原生 Responses HTTP JSON POST，不再经由 Spring AI `ChatCompletionRequest`；上游 Responses 原始字段、官方 headers 与 usage 映射已有本地 HTTP server 回归，streaming SSE 透明转发和 lifecycle endpoints 继续保留为后续切片。
- 已闭环 `TASK-20260515-018`：作为 `TASK-20260514-018` 的本地 lifecycle 切片，stored Responses 已补齐 `POST /v1/responses/{id}/cancel` 与 `GET /v1/responses/{id}/input_items`，覆盖状态流转、input item list envelope、分页参数、公开文档和 OpenAPI snapshot。
- 已启动 `TASK-20260515-019`：作为 `TASK-20260514-018` 的 streaming 参数切片，目标是让 Responses stream delta events 支持 `stream_options.include_obfuscation` 默认开启与显式关闭。
