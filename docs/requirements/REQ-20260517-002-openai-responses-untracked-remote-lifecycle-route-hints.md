# REQ-20260517-002 OpenAI Responses 无 Lineage 远端 Lifecycle Route Hint

状态：Done
日期：2026-05-17
来源任务：[TASK-20260514-018](../../tasks/done/TASK-20260514-018-openai-responses-native-lifecycle.md)

## 背景

`TASK-20260516-001` 已经让 gateway-created 且带 upstream lineage 的 stored Response 支持远端 retrieve/delete/cancel/input_items passthrough。但如果客户端只持有真实 OpenAI 返回的未知 `resp_...` id，网关本地没有 lineage，当前只能返回本地资源不存在。父任务 `TASK-20260514-018` 把“任意未知远端 `resp_...` id 的无 lineage 盲路由”列为剩余切片，并指出需要明确 model/credential 或远端对象发现机制。

## 目标

- 对无本地 lineage 的 Responses lifecycle endpoint 增加显式 route hint passthrough。
- 只有请求提供 `model` query 或 `X-AI-Gateway-OpenAI-Model` header 时，才尝试远端 OpenAI Direct passthrough。
- 支持 `GET /v1/responses/{responseId}`、`DELETE /v1/responses/{responseId}`、`POST /v1/responses/{responseId}/cancel` 与 `GET /v1/responses/{responseId}/input_items`。
- 远端 passthrough 保留 include/after/limit/order query，并保留上游 HTTP status 与 error body。
- 没有 route hint 时仍保持本地 not found，不做盲路由、不猜测 credential。
- 更新 public docs、provider catalog conformance、父任务和测试。

## 非目标

- 不实现远端对象全局发现。
- 不在没有 model/credential hint 时扫描所有 OpenAI Direct 凭证。
- 不改变已有 gateway-created stored Response 的 local lineage 优先级。
- 不为 OpenAI-compatible Generic 承诺无 lineage lifecycle passthrough。

## 方案

1. `OpenAiResponsesController` lifecycle endpoints 在本地 `GatewayAsyncResourceService` 命中失败且错误为“未找到指定的异步资源对象”时，检查 `model` query 或 `X-AI-Gateway-OpenAI-Model` header。
2. 如果没有 route hint，沿用现有错误。
3. 如果存在 route hint，调用 `GatewayOpenAiPassthroughService` 新增的 OpenAI Direct lifecycle JSON passthrough，使用 hint 参与路由选择，但上游请求本身不注入 body。
4. `GatewayOpenAiPassthroughService` 新增 GET/DELETE/POST lifecycle passthrough，要求选中 provider 为 `OPENAI_DIRECT`。
5. 控制器测试覆盖：无 hint 不 passthrough；有 hint retrieve/delete/cancel/input_items 分别转发正确 path 与 query；上游错误 status 原样返回。

## 影响范围

- `OpenAiResponsesController`
- `GatewayOpenAiPassthroughService`
- `OpenAiResponsesControllerTests`
- `PublicDocsBundleService`
- `provider-catalog.json`
- `TASK-20260514-018`
- OpenAI 覆盖报告

## 风险

- 无 hint 盲路由会误用账号或泄露跨租户对象；必须禁止。
- 远端 delete/cancel 是写操作，必须由调用方显式给出 model hint 才允许走 OpenAI Direct 路由。
- 如果本地对象存在但 lifecycle 状态非法，不应 fallback 到远端。

## 验收标准

- 本地对象存在时仍优先走本地/lineage 逻辑。
- 未找到本地对象且没有 route hint 时不调用 passthrough。
- 未找到本地对象且有 route hint 时，retrieve/delete/cancel/input_items 调用 OpenAI Direct lifecycle passthrough。
- include/after/limit/order query 能在 input_items 远端 passthrough 中保留。
- 上游错误 status 和 body 不被本地 fallback 吞掉。
- 聚焦测试通过。

## 测试边界

- `OpenAiResponsesControllerTests`
- 不访问真实 OpenAI。

## 实现结果

- `OpenAiResponsesController` 的 retrieve/delete/cancel/input_items endpoint 改为本地优先；只有本地资源不存在且请求带 `model` query 或 `X-AI-Gateway-OpenAI-Model` header 时，才进入 OpenAI Direct lifecycle passthrough。
- `GatewayOpenAiPassthroughService` 新增 `executeOpenAiDirectLifecycleJson`，支持 GET/DELETE/POST lifecycle 请求，不注入上游 body，且要求路由命中 `OPENAI_DIRECT`。
- input_items passthrough 会保留 `include`、`after`、`limit`、`order` query；`model` 只作为 gateway route hint，不转发给上游。
- 上游 HTTP status 与 error body 原样返回，不被本地 fallback 吞掉。
- Public docs、public OpenAPI、provider catalog conformance、父任务与覆盖报告已同步 `openai.responses-untracked-remote-lifecycle-route-hints`。

## 验证记录

2026-05-17 已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

## 遗留问题

- 无 `model`/header hint 的任意远端 `resp_...` 盲路由保持非目标。
- 真实 OpenAI Direct Responses live smoke 仍由 `TASK-20260514-031` 的受控 key、预算与 record/replay 体系承接。
