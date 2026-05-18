# TASK-20260516-002 OpenAI Responses input_tokens Native Passthrough

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)  
上游来源：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)、[TASK-20260515-020](../done/TASK-20260515-020-openai-responses-input-tokens-compact-baseline.md)、[TASK-20260516-001](../done/TASK-20260516-001-openai-responses-remote-lifecycle-passthrough-baseline.md)

## 背景

`POST /v1/responses/input_tokens` 已有本地 deterministic estimate，可避免 SDK 404，但它不是 OpenAI 官方 tokenizer 结果，也不能覆盖 reasoning、conversation、tool schema、truncation 等官方语义。OpenAI API Reference 中该 endpoint 用于返回请求输入 token 数，响应 shape 为 `object=response.input_tokens` 与 `input_tokens`。在 OpenAI Direct 可用时，应优先走 native upstream，只有无法建立路由或显式不走 native 时才保留本地估算作为 fallback。

## 目标

- 为 `POST /v1/responses/input_tokens` 增加 OpenAI Direct/native passthrough 路径，向上游发送原始 request body。
- 保留本地 deterministic estimate fallback，避免没有可用 native route 的社区 key 直接失效。
- fallback 只覆盖路由缺失或 upstream capability 不可用类错误；对于上游已执行后的 4xx/5xx，不静默吞掉，避免把真实请求错误伪装成估算值。
- 公开文档明确 native 计数与本地 estimate 的边界。

## 非目标

- 不实现 `responses/compact` native passthrough。
- 不引入本地 tokenizer 依赖或模型级 tokenizer 表。
- 不改变 `/v1/responses` create/stream 和 stored Response lifecycle 行为。
- 不支持没有 `model` 且也没有 default model 的 input_tokens 请求；仍按现有路由规则处理。

## 输入

- OpenAI Responses input_tokens API reference。
- `OpenAiResponsesController`、`OpenAiResponsesLocalLifecycleService`。
- `GatewayOpenAiPassthroughService` 现有 OpenAI-style JSON passthrough 能力。

## 输出

- input_tokens native passthrough service/controller wiring。
- 本地 fallback 边界和单元测试。
- 公开 docs bundle、OpenAPI snapshot 与任务文档更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesLocalLifecycleService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayOpenAiPassthroughService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayOpenAiPassthroughServiceTests.java`
- `docs/public-api-compatibility.md`
- `docs/openapi/public-openapi.json`

## 依赖

- `TASK-20260515-020` 的本地 baseline。
- `GatewayOpenAiPassthroughService` 的模型路由、认证、headers 与错误规则。

## 风险

- 如果直接 catch 所有 upstream 异常并 fallback，本来应该暴露的 400/401/429/500 会被隐藏；实现必须只对路由缺失/不支持类错误 fallback。
- `GatewayOpenAiPassthroughService.executeJson` 默认会补写 resolved model；这符合 OpenAI native endpoint 需要 model 的行为，但测试需要确认 request body 不丢失 input/tools/reasoning 等字段。
- 第三方 OpenAI-compatible 站点可能不支持 input_tokens；本切片的文档要说明 native passthrough 以 OpenAI Direct 为准。

## 验收标准

- 有可用 OpenAI Direct route 时，`POST /v1/responses/input_tokens` 调用上游 `/v1/responses/input_tokens` 并返回上游 `object=response.input_tokens`、`input_tokens`。
- 无可用 route 或不支持 native passthrough 时，仍返回本地 deterministic estimate。
- 上游已执行后的错误不被静默改成本地 estimate。
- Targeted tests 通过，并回写父任务、报告和任务索引。

## 测试边界

- 不访问真实 OpenAI 远端。
- controller test 使用 mock passthrough service 覆盖 native 成功、fallback、错误不吞。
- passthrough service test 使用 fake WebClient 验证路径、method 与 request body。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)
- OpenAI Responses input_tokens: https://platform.openai.com/docs/api-reference/responses/retrieve

## 实现结果

- `GatewayOpenAiPassthroughService` 新增 `executeOpenAiDirectJson`，在现有 OpenAI-style JSON passthrough 上增加 OpenAI Direct route 约束；若路由选到 OpenAI-compatible，会抛出明确 `IllegalArgumentException` 且不污染 route cooldown。
- `OpenAiResponsesController.countInputTokens` 改为优先调用 `executeOpenAiDirectJson("/v1/responses/input_tokens")`，成功时保留上游 `ResponseEntity` 和 HTTP status；仅在本地前置校验、无路由或非 OpenAI Direct route 等 `IllegalArgumentException` 场景下回退本地 deterministic estimate。
- 上游已执行后的 4xx/5xx 以 `ResponseEntity` 形式透出，不会被静默改写成本地 estimate。
- 公开 docs bundle、OpenAPI snapshot 与兼容文档已标注 OpenAI Direct native input_tokens 与本地 estimate fallback 边界。

## 验证情况

- `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayOpenAiPassthroughServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests"`
- `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayOpenAiPassthroughServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`

## 遗留问题

- `responses/compact` native passthrough 已由 [TASK-20260516-003](TASK-20260516-003-openai-responses-compact-native-passthrough.md) 闭环。
- 本切片未引入本地模型级 tokenizer；本地 fallback 仍是 deterministic estimate。
