# TASK-20260517-002 OpenAI Responses 无 Lineage 远端 Lifecycle Route Hint

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)
上游来源：[REQ-20260517-002](../../docs/requirements/REQ-20260517-002-openai-responses-untracked-remote-lifecycle-route-hints.md)

## 背景

Responses 远端 lifecycle passthrough 目前依赖 gateway-created stored Response 的 upstream lineage。未知 `resp_...` id 没有本地 lineage 时不能安全盲路由；需要用显式 route hint 把远端对象归属到明确模型与 OpenAI Direct 路由。

## 目标

- 增加 `model` query / `X-AI-Gateway-OpenAI-Model` header route hint。
- 对 retrieve/delete/cancel/input_items 支持无 lineage 远端 passthrough。
- 无 hint 时保持本地 not found，不猜 credential。
- 保留远端 status/error body。
- 补控制器测试、文档和任务回写。

## 非目标

- 不做凭证扫描或远端对象发现。
- 不改变本地对象优先级。
- 不为 OpenAI-compatible Generic 声明远端 lifecycle passthrough。

## 输入

- `OpenAiResponsesController`
- `GatewayOpenAiPassthroughService`
- `GatewayAsyncResourceService`
- `OpenAiResponsesControllerTests`
- `TASK-20260514-018` 剩余切片

## 输出

- Route hint lifecycle passthrough 实现。
- Controller regression tests。
- public docs / provider catalog conformance / 父任务回写。

## 影响范围

- OpenAI Responses lifecycle endpoints。
- OpenAI Direct passthrough service。
- Public docs 和 provider catalog。

## 依赖

- [TASK-20260516-001 OpenAI Responses 远端生命周期 Passthrough 基线](TASK-20260516-001-openai-responses-remote-lifecycle-passthrough-baseline.md)
- [TASK-20260514-018 OpenAI Responses 原生执行器与生命周期](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)

## 风险

- 写操作误路由：delete/cancel 只有显式 hint 才允许。
- 本地状态错误被误吞：只有本地资源不存在时才 fallback。
- Query 拼接错误会破坏 include/input_items 官方形态。

## 验收标准

- 未找到本地对象且无 hint 时不调用 passthrough。
- 未找到本地对象且有 hint 时调用 OpenAI Direct lifecycle passthrough。
- input_items 的 include/after/limit/order 能保留。
- 上游错误 status/body 原样返回。
- 聚焦测试通过。

## 测试边界

- `OpenAiResponsesControllerTests`
- 无网络、无真实 key。

## 关联文档

- [REQ-20260517-002](../../docs/requirements/REQ-20260517-002-openai-responses-untracked-remote-lifecycle-route-hints.md)
- [TASK-20260514-018](../backlog/TASK-20260514-018-openai-responses-native-lifecycle.md)

## 当前状态

- 2026-05-17：任务创建，进入实现。
- 2026-05-17：实现、文档、provider conformance、public OpenAPI 与聚焦测试已完成，移动到 `tasks/done/`。

## 实现结果

- `OpenAiResponsesController` 本地优先处理 retrieve/delete/cancel/input_items；本地资源不存在且带 `model` query 或 `X-AI-Gateway-OpenAI-Model` header 时，才走远端 route-hint passthrough。
- `GatewayOpenAiPassthroughService` 新增 `executeOpenAiDirectLifecycleJson`，支持 OpenAI Direct GET/DELETE/POST lifecycle 请求并保留上游 HTTP status/body。
- Route hint 只用于网关选路，不作为上游 query 转发；`include`、`after`、`limit`、`order` 会保留到上游 path。
- Public docs、OpenAPI、provider catalog conformance 和父任务已同步 `openai.responses-untracked-remote-lifecycle-route-hints`。

## 验证记录

2026-05-17 已通过：

```powershell
.\gradlew.bat test --rerun-tasks --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoaderTests"
```

## 遗留问题

- 无 `model`/header hint 的任意远端 `resp_...` 盲路由保持非目标。
- 真实 OpenAI Direct Responses live smoke 仍由 `TASK-20260514-031` 的受控 key、预算与 record/replay 体系承接。
