# TASK-20260514-009 OpenAI/xAI Responses 字段 parity 与 cache/header 保真

状态：Done  
优先级：High  
类型：子任务  
父任务：[TASK-20260514-006](../done/TASK-20260514-006-community-home-api-docs-refresh.md)  
上游来源：[REP-20260514 主流厂商 API/changelog 复核](../../docs/reports/REP-20260514-mainstream-api-changelog-refresh.md)、[REQ-20260514-007](../../docs/requirements/REQ-20260514-007-mainstream-api-parity-backlog-closure.md)

## 本轮设计

- Responses 原始请求继续保存在 `providerExtensions`，作为字段保真事实源。
- Native OpenAI-compatible runtime 对 Chat SDK 已支持字段直接映射：`service_tier`、`parallel_tool_calls`、`prompt_cache_key`、`top_logprobs`。
- Responses-only 字段通过 `extraBody` 受控保留：`truncation`、`text`、`prompt_cache_retention`、`safety_identifier` 等，避免静默丢失。
- xAI prompt cache header 由 `prompt_cache_key` 生成 `x-grok-conv-id`，在 per-request OpenAI-compatible client 构建时下发。

## 背景

OpenAI 与 xAI 都把 Responses API 作为主力入口，字段包含 `service_tier`、`parallel_tool_calls`、`prompt_cache_key`、`truncation`、`text`、server-side tools、reasoning summary 和 provider-specific cache headers。当前项目已有 `/v1/responses` 与 Codex metadata，但 Native runtime 仍主要复用 Chat SDK 参数子集。

## 目标

- 建立 OpenAI/xAI Responses 字段白名单与保真矩阵。
- 让 `service_tier`、`parallel_tool_calls`、`prompt_cache_key`、`truncation`、`text`、`top_logprobs` 等字段在支持的 provider 上可验证地下发。
- 为 xAI 增加 `x-grok-conv-id` 或等价 prompt cache 亲和 header 的策略设计。

## 非目标

- 不在本任务内实现所有 server-side tools 的真实执行。
- 不改变现有 Chat Completions 兼容行为。

## 输入

- OpenAI Responses API 官方文档。
- xAI Chat/Responses API、Prompt Caching 与 2026-05-15 model retirement 官方说明。
- 当前 `OpenAiResponsesRequestMapper`、`OpenAiNativeGatewayChatRuntime`、Codex smoke。

## 输出

- Responses 字段 parity 实现或明确降级记录。
- xAI cache header/slug redirect 的 conformance 测试。
- 公开文档与 provider catalog 同步。

## 验收标准

- 覆盖 OpenAI 与 xAI 至少各一条 Responses 字段保真单测。
- 字段不支持时返回可解释降级，不静默丢失。
- xAI 退役模型重定向对成本/模型选择的影响在 catalog 或 docs 中可见。

## 测试边界

- Java mapper/runtime 单测。
- Codex/xAI Responses smoke 可在缺 key 时分类 SKIPPED。

## 完成结果

- `OpenAiResponsesRequestMapper` 支持从嵌套 `reasoning.effort` 补齐 canonical reasoning。
- `OpenAiNativeGatewayChatRuntime` 下发 `service_tier`、`parallel_tool_calls`、`prompt_cache_key`、`top_logprobs`、`safety_identifier`、`verbosity` 与 metadata；Responses-only 字段进入 OpenAI-compatible extra body。
- `OpenAiChatModelFactory` 支持 per-request header，Grok 场景通过 `x-grok-conv-id` 保留 prompt cache affinity。

## 验证记录

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.OpenAiNativeGatewayChatRuntimeTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests"
```

结果：通过。
