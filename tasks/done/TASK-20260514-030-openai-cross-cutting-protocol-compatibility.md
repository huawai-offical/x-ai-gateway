# TASK-20260514-030 OpenAI 横切协议兼容

状态：Completed  
优先级：Critical  
类型：子任务  
父任务：[TASK-20260514-016](../backlog/TASK-20260514-016-functional-service-api-coverage-parent.md)  
上游来源：[TASK-20260514-013](TASK-20260514-013-openai-chat-responses-native-parity.md)、[TASK-20260514-014](TASK-20260514-014-openai-resource-family-coverage-gap.md)

## 背景

即使 endpoint 补齐，如果 headers、errors、pagination、idempotency、stream event、webhook signature、request id、organization/project scope、rate limit shape 不一致，也不能称为完全兼容。

## 目标

- 建立 OpenAI 横切协议兼容规范。
- 统一 Authorization、OpenAI-Organization、OpenAI-Project、Idempotency-Key、request id、user agent、beta header 等处理。
- 对齐 error object、HTTP status、rate limit headers、pagination、deleted object、cancel/pause/resume 状态。
- 覆盖 SSE、WebSocket/WebRTC event、webhook signature 和 replay protection。

## 非目标

- 不在本任务内补齐具体业务 endpoint。
- 不为第三方 provider 强制注入 OpenAI Direct 专用 headers。

## 输入

- 官方 OpenAI API Reference 横切说明。
- 全部 OpenAI controller/runtime/resource executor。
- Global exception handler、request lifecycle、observability、security filter。

## 输出

- Cross-cutting compatibility spec。
- 通用 header/error/pagination/idempotency utilities。
- 全局测试夹具和 negative tests。

## 影响范围

- WebFlux filters、exception handler、gateway execution service、resource executor、conformance tests、docs。

## 依赖

- 所有 OpenAI API 子任务。

## 风险

- 改动全局错误模型可能影响现有客户。
- Idempotency 和 replay protection 需要持久化策略。

## 验收标准

- 每个 OpenAI endpoint 使用统一错误和 request id 规范。
- Pagination、idempotency 和 rate limit 行为有契约测试。
- Webhook signature 和 streaming event 有官方形态兼容测试。

## 测试边界

- Global exception tests。
- Filter/header tests。
- Contract tests 覆盖分页、错误、幂等和 webhook replay。

## 已完成切片

- [TASK-20260515-001 OpenAI 错误 Envelope 与 Request Id 基线](../done/TASK-20260515-001-openai-error-request-id-foundation.md)：OpenAI path 错误体切换为 `error` envelope，`TraceIdWebFilter` 同时回写 `X-Request-Id` 与 `X-Trace-Id`，并通过 OpenAI/Anthropic/Gemini 代表性 ingress 回归。
- [TASK-20260515-002 OpenAI 官方 Headers 与 Idempotency-Key 下发基线](../done/TASK-20260515-002-openai-official-headers-idempotency-foundation.md)：Chat/Responses 捕获 `OpenAI-Organization`、`OpenAI-Project`、`Idempotency-Key` 到 canonical metadata，native runtime 仅向 `OPENAI_DIRECT` 下发官方 headers，并保留 Grok 会话头白名单。
- [TASK-20260515-009 OpenAI Idempotency-Key 本地响应持久化与重放基线](../done/TASK-20260515-009-openai-idempotency-key-response-replay.md)：为非流式 Chat/Responses create 建立本地幂等记录，按 distributed key、path、key 与 request fingerprint 重放最终 JSON payload，不同请求体复用同 key 会按 OpenAI-style 错误拒绝。
- [TASK-20260515-010 OpenAI List Pagination Envelope 与 Cursor 参数基线](../done/TASK-20260515-010-openai-list-pagination-envelope-cursor-baseline.md)：stored Chat list/messages 对齐 `after`、`limit`、`order`，默认 `limit=20`、`order=asc`，非法参数返回 OpenAI-style 错误，并在 public OpenAPI/docs bundle 中公开 list envelope 契约。
- [TASK-20260515-011 OpenAI Rate Limit Headers 与 429 错误基线](../done/TASK-20260515-011-openai-rate-limit-headers-baseline.md)：OpenAI path 本地限流命中返回 HTTP 429、`rate_limit_error` 与基础 `Retry-After`/`x-ratelimit-*` remaining/reset headers，普通参数错误保持 400。
- [TASK-20260515-012 OpenAI Idempotency-Key TTL 与清理策略](../done/TASK-20260515-012-openai-idempotency-ttl-cleanup.md)：幂等记录默认保留 24 小时，提供 `purgeExpiredRecords` 手动清理方法，并通过 scheduled cleanup 默认每小时清理过期记录。
- [TASK-20260515-013 OpenAI Webhook Signature 与 Replay 防护基线](../done/TASK-20260515-013-openai-webhook-signature-replay-baseline.md)：按 Standard Webhooks 形态校验 `webhook-id`、`webhook-timestamp`、`webhook-signature`，支持 `whsec_` 与 raw secret，并用 `RateLimitStore` 提供 24 小时 replay marker。
- [TASK-20260515-014 OpenAI Streaming Event Usage 与 Sequence 基线](../done/TASK-20260515-014-openai-streaming-event-usage-sequence-baseline.md)：Chat stream 支持 `stream_options.include_usage` usage chunk 并稳定 chunk id/created，Responses stream event 增加本地递增 `sequence_number`。
- [TASK-20260515-015 OpenAI Protocol Path Matcher 覆盖防遗漏基线](../done/TASK-20260515-015-openai-protocol-path-matcher-coverage-baseline.md)：提取 `OpenAiProtocolPathMatcher` 并增加 OpenAI/non-OpenAI path matrix，防止新增 endpoint 漏掉 OpenAI-style error envelope 和 429 headers。
- [TASK-20260516-004 OpenAI Stored Chat 数据库游标分页与过滤硬化](../done/TASK-20260516-004-openai-stored-chat-db-pagination-filter-hardening.md)：stored Chat list 已从固定 scan window 改为数据库游标分页，使用 `createdAt + id` 稳定排序，并保留 OpenAI list envelope 与 metadata 精确过滤。
- [TASK-20260516-008 OpenAI Realtime WebSocket 入口与事件代理基线](../done/TASK-20260516-008-openai-realtime-websocket-ingress-event-proxy.md)：`/v1/realtime?model=...` 已提供 WebSocket handler，复用 Distributed Key 鉴权与 `openai_realtime` Live Session，输出 `session.created/session.updated/error` 等 OpenAI-style 事件基线，并把客户端 JSON event 转发到 Live Session 事件流。

## 剩余切片

- WebRTC、SIP、Realtime calls、Realtime translation/transcription session、真实上游二进制音频帧透传不再归入本横切基线的当前完成范围。
- 新增 OpenAI endpoint 时需要同步更新 `OpenAiProtocolPathMatcherTests` 的 path matrix。

## 当前状态

- 2026-05-21：错误 envelope、request id、headers、idempotency、pagination、rate limit、webhook signature、streaming event 与 protocol path matcher 基线均已由下游切片闭环；本任务归档至 `tasks/done/`。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
