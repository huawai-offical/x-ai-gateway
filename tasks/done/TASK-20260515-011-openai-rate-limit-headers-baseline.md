# TASK-20260515-011 OpenAI Rate Limit Headers 与 429 错误基线

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-030](../done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
上游来源：[REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)、[TASK-20260515-001](../done/TASK-20260515-001-openai-error-request-id-foundation.md)

## 背景

OpenAI 客户端和 SDK 会依赖 HTTP 429、`rate_limit_error`、`rate_limit_exceeded` 与 rate limit response headers 判断退避和重试。目前本项目已有 Distributed Key RPM/TPM/concurrency、Route Policy runtime rate window 与 provider 429 重试逻辑，但 OpenAI path 的本地限流命中仍可能以普通 `invalid_request_error` 或候选不可用 400 返回，也不会附带 OpenAI-style rate limit headers。这会让客户端无法稳定识别限流。

## 目标

- OpenAI protocol path 本地限流命中时返回 HTTP 429。
- OpenAI error envelope 使用 `error.type=rate_limit_error`、`error.code=rate_limit_exceeded`。
- 对 429 响应写入基础退避 headers：`Retry-After`、`x-ratelimit-remaining-requests`、`x-ratelimit-remaining-tokens`、`x-ratelimit-reset-requests`、`x-ratelimit-reset-tokens`。
- 覆盖 `IllegalArgumentException` 中的本地 RPM/TPM/concurrency/route_policy_rate_limited 文本，以及已有 `GatewayRuleMatchedException(429)`。
- Public docs 与 docs bundle 说明当前 header 是本地退避基线，精确 limit 值后续随 governance snapshot 传递。

## 非目标

- 不重写 `RateLimitStore` 或 route policy runtime store。
- 不在本轮把真实窗口剩余量、limit 值和 reset 时间从 governance service 全链路传到 exception。
- 不处理 upstream provider 原样透传的 429 headers；passthrough/service executor 的 upstream header 保留由资源执行链路单独处理。

## 输入

- `GlobalApiExceptionHandler`
- `TraceIdWebFilter`
- `DistributedKeyGovernanceService`
- `RoutingPolicyRuntimeEnforcementService`
- OpenAI Rate limits headers 官方说明

## 输出

- OpenAI path rate limit 识别与 429 响应基线。
- 429 response headers 基线。
- 全局 exception handler 回归测试。
- Public docs 说明。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/infra/config/web/GlobalApiExceptionHandler.java`
- `src/test/java/com/prodigalgal/xaigateway/infra/config/web/GlobalApiExceptionHandlerTests.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleServiceTests.java`
- `docs/public-api-compatibility.md`
- `tasks/index.md`

## 依赖

- `TASK-20260515-001` 已提供 OpenAI path error envelope 与 request id header。
- `TASK-20260501-025` 已提供 route policy runtime rate limit 执行基础。

## 风险

- 文本识别限流原因只能作为横切基线，长期更理想的方案是让 governance decision 携带结构化 rate limit reason。
- 当前 header 的 limit 值未知，因此本轮只写入 remaining/reset/retry-after，不伪造 `x-ratelimit-limit-*`。
- 将本地限流从 400 修正为 429 是兼容性修复，但可能改变依赖旧错误码的内部脚本。

## 验收标准

- OpenAI path 上 RPM/TPM/concurrency/route policy rate limited 文本返回 HTTP 429。
- 响应体为 OpenAI-style `rate_limit_error`。
- 响应头包含 `Retry-After` 和 OpenAI-style reset/remaining headers。
- 非 OpenAI path 不被强制套用 OpenAI error envelope。
- docs bundle 与 Markdown 文档公开说明 rate limit headers。

## 测试边界

- `GlobalApiExceptionHandlerTests` 覆盖 IllegalArgument rate limit 和 GatewayRuleMatchedException 429。
- `PublicDocsBundleServiceTests` 覆盖文档说明。
- 不跑真实 provider smoke。

## 实现结果

- `GlobalApiExceptionHandler`：
  - 本地限流文本包含 RPM、TPM、并发、限流、`rate limit`、`rate_limited` 或 `too many requests` 时，OpenAI path 返回 HTTP 429；
  - 429 OpenAI path 统一写入 `Retry-After: 60`、`x-ratelimit-remaining-requests: 0`、`x-ratelimit-remaining-tokens: 0`、`x-ratelimit-reset-requests: 60s`、`x-ratelimit-reset-tokens: 60s`；
  - `GatewayRuleMatchedException(429)` 也会走同一 header 基线；
  - 普通参数错误仍保持 HTTP 400 与 `invalid_request_error`。
- `PublicDocsBundleService` 与 `docs/public-api-compatibility.md`：
  - 公开说明 OpenAI path 本地限流会返回 429、`rate_limit_error`、`Retry-After` 与 `x-ratelimit-*` remaining/reset headers；
  - 明确当前不伪造 `x-ratelimit-limit-*`，后续等 governance snapshot 结构化传递后再补 precise limit。
- `GlobalApiExceptionHandlerTests` 新增本地 rate limit 与 `GatewayRuleMatchedException(429)` 回归。

## 验证结果

- 首次验证失败：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.infra.config.web.GlobalApiExceptionHandlerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests"` 暴露 `Optional.map(...).orElseGet(...)` wildcard 推断编译错误。
- 已修复：把 `Optional.map` 返回显式声明为 `ResponseEntity<?>`。
- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.infra.config.web.GlobalApiExceptionHandlerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests"`

## 遗留问题

- 当前 header 是退避基线，不包含 precise `x-ratelimit-limit-*`；后续需要让 governance/routing decision 携带结构化 limit、remaining、reset。
- Upstream provider 429 原始 header 透传未在本轮扩展。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [TASK-20260514-030](../done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
