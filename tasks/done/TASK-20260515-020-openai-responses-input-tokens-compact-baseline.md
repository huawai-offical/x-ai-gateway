# TASK-20260515-020 OpenAI Responses input_tokens 与 compact 本地基线

状态：Done  
优先级：Critical  
类型：子任务切片  
父任务：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)  
上游来源：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)、OpenAI Responses API Reference

## 背景

`TASK-20260514-018` 仍缺 `POST /v1/responses/input_tokens` 与 `POST /v1/responses/compact`。官方 Responses API 中，`input_tokens` 用于返回请求输入 token 数，`compact` 用于对长上下文执行压缩并返回 `response.compaction` 对象。当前网关没有这些入口，客户端如果按 Responses SDK 调用会遇到 404 或 OpenAI-style path 缺口。

## 目标

- 新增 `POST /v1/responses/input_tokens`，返回 OpenAI-compatible `object=response.input_tokens` 与 `input_tokens`。
- 新增 `POST /v1/responses/compact`，返回 OpenAI-compatible `object=response.compaction`、`id`、`created_at`、`output` 与 `usage`。
- 对 string input、message array / item array、instructions 做确定性本地统计或结构化输出。
- 公开 docs bundle 与 OpenAPI snapshot 标明本地基线属于 deterministic local estimate / emulation，不伪装为 OpenAI tokenizer 或真实模型 compaction。

## 非目标

- 不实现 OpenAI Direct 远端 passthrough。
- 不实现模型精确 tokenizer；本切片只提供稳定、可测试、可说明的本地估算。
- 不保存 compact 结果为 stored Response，也不引入 Conversations 状态。

## 输入

- `OpenAiResponsesController` request body。
- OpenAI Responses API Reference 中 `responses/input_tokens` 与 `responses/compact` endpoint shape。

## 输出

- Responses input token count endpoint。
- Responses compact endpoint。
- Controller、public docs bundle 与 OpenAPI snapshot 回归。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesController.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesLocalLifecycleService.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/publicapi/PublicDocsBundleService.java`
- `docs/openapi/public-openapi.json`
- `docs/public-api-compatibility.md`

## 依赖

- `TASK-20260515-018` 已补本地 stored Responses lifecycle。
- `TASK-20260515-019` 已补 Responses stream options 基线。

## 风险

- 估算 token 数如果被误认为精确计费依据，会误导客户；公开文档必须明确它是 local estimate。
- compact 输出如果泄露原始上下文过多，会削弱“压缩/opaque”语义；本切片需要只保留调用方可继续传给 `/v1/responses` 的结构，并使用 opaque compaction marker 表示本地压缩结果。

## 验收标准

- `POST /v1/responses/input_tokens` 通过 Bearer auth 后返回 `object=response.input_tokens` 与非负 `input_tokens`。
- 相同请求体返回稳定 token estimate；更长 input 返回不小于短 input 的 estimate。
- `POST /v1/responses/compact` 返回 `object=response.compaction`，包含可复用的 `output` 数组和 `usage.input_tokens/total_tokens`。
- 未授权或无效 Bearer token 仍走现有认证错误路径。
- 公开 docs bundle 与 OpenAPI snapshot 包含两个 endpoint。

## 测试边界

- Controller WebFlux tests。
- Public docs bundle tests。
- Public OpenAPI snapshot tests。
- 不访问真实 OpenAI 远端。

## 关联文档

- [REQ-20260514-009](../../docs/requirements/REQ-20260514-009-openai-full-api-coverage-task-system.md)
- [REP-20260514 OpenAI 全量覆盖任务拆解](../../docs/reports/REP-20260514-openai-full-api-coverage-task-breakdown.md)

## 实现结果

- 新增 `OpenAiResponsesLocalLifecycleService`，提供 Responses input token deterministic estimate 与 local compaction emulation。
- `OpenAiResponsesController` 新增 `POST /v1/responses/input_tokens` 与 `POST /v1/responses/compact`，均沿用 Distributed Key Bearer 认证。
- `input_tokens` 返回 `object=response.input_tokens` 与非负 `input_tokens`；OpenAI Direct native passthrough 后续已由 [TASK-20260516-002](TASK-20260516-002-openai-responses-input-tokens-native-passthrough.md) 闭环。
- `compact` 返回 `object=response.compaction`、`id`、`created_at`、`output`、opaque compaction marker 与 `usage`。
- 公开 docs bundle、OpenAPI snapshot 与兼容文档已说明本地 estimate / emulation 边界。

## 验证情况

- `.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests"`

## 遗留问题

- 本切片不提供 OpenAI 官方 tokenizer 精确计数。
- 本切片不执行远端模型 compaction 或 OpenAI Direct passthrough；`responses/input_tokens` native passthrough 已由 [TASK-20260516-002](TASK-20260516-002-openai-responses-input-tokens-native-passthrough.md) 闭环，`responses/compact` native passthrough 已由 [TASK-20260516-003](TASK-20260516-003-openai-responses-compact-native-passthrough.md) 闭环。
