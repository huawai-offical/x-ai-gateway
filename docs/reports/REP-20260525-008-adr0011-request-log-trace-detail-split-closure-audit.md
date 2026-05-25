# REP-20260525-008 ADR-0011 请求日志与请求详情拆表闭环审计

状态：Done  
日期：2026-05-26  
上游来源：用户目标“闭环 ADR-0011 请求日志主记录与请求详情追踪拆表”。

## 审计目标

对 [ADR-0011](../decisions/ADR-0011-request-log-trace-detail-split.md) 做逐项证据审计，确认当前实现不只是新增文档，而是真正满足请求主记录、请求详情、归档摘要和 Admin 聚合展示的设计约束。

## ADR 验收项

1. `request_log` 只保存轻量主记录、状态、路由摘要、耗时、错误和健康统计字段，不保存完整上下游 payload。
2. `request_trace_detail` 通过 `request_id` 关联主日志，按阶段记录下游请求、canonical 请求、转换计划、上游请求、上游响应、下游响应和错误摘要。
3. `request_trace_detail_archive` 记录 TTL 清理批次、过期窗口、清理条数、阶段计数和状态。
4. Admin API 和前端可以聚合展示主日志与请求详情，不把完整 payload 合并进 `request_log`。
5. trace detail 默认脱敏、截断、hash、长度统计；敏感字段和完整二进制不落库。
6. trace detail 支持默认全量、配置化采样、稳定 requestId 采样、TTL 和先归档摘要再删除。
7. 无法获得真实 HTTP wire body 的阶段必须在 metadata 中标明来源和限制，避免把结构化摘要误认为原始 wire dump。

## 当前证据

- `RequestLogEntity` 当前没有 `payload_json`、`request_body`、`response_body` 等正文列。
- `db.changelog-0001-baseline.yaml` 的 `request_log` 段仅包含请求主记录字段；全库其它表存在 payload 字段不代表 `request_log` 存正文。
- `RequestTraceDetailEntity` 已包含 `requestId`、`stage`、`direction`、`contentKind`、`payloadJson`、`metadataJson`、hash、长度、脱敏、截断和 `expiresAt`。
- `RequestTraceDetailArchiveEntity` 已包含 `archiveBatchId`、`cutoffAt`、`archivedCount`、窗口时间、`stageCountsJson` 和状态。
- `GatewayRequestTraceDetailService` 已实现脱敏、截断、hash、采样、TTL 和归档删除。
- `ObservabilityTraceResponse` 已包含 `requestLog` 与 `traceDetails`。
- 前端链路追踪页已展示 trace detail，request logs 页面深链到链路追踪。
- `GatewayChatExecutionService` 与 `GatewayResourceExecutionService` 已在 trace metadata 中写入 `payloadSource`、`wireBody` 和 `wireBodyLimitation`。
- `GatewayChatExecutionServiceTests` 与 `GatewayResourceExecutionServiceTests` 已验证 `UPSTREAM_REQUEST` 是结构化摘要，并显式标注不是 raw upstream HTTP wire body。
- `ADR-0011` 已补充闭环证据，回链本报告与 `TASK-20260525-008`。

## 发现缺口

- 已补齐。trace detail 阶段现在统一写入 `payloadSource`、`wireBody` 和 `wireBodyLimitation`，其中 `UPSTREAM_REQUEST` 与 `UPSTREAM_RESPONSE` 明确标记为网关/runtime/executor 结构化摘要，不是上游 HTTP raw wire body。

## 本轮闭环范围

- 增加 trace metadata 来源和限制字段。
- 覆盖 chat 与 resource 两条执行链路。
- 增加定向测试，证明非 wire body 阶段会携带来源和限制。
- 回写 ADR、需求和任务文档的验收结果。

## 非目标

- 不引入对象存储正文归档。
- 不新增归档查询 UI。
- 不改变 `request_log` 与 `request_trace_detail` 的物理拆表设计。
- 不扩大到真实 provider smoke。

## 验收方式

- 后端定向测试覆盖 trace detail service、chat/resource metadata 来源标记、query/controller 映射。
- 前端 trace 页定向测试确认 metadata 字段仍可展示。
- 前端类型检查通过。

## 验收结果

- 表结构边界：`request_log` 主记录不保存完整 payload；`request_trace_detail` 保存请求详情；`request_trace_detail_archive` 保存 TTL 清理摘要。
- 查询边界：`ObservabilityTraceResponse` 聚合 `requestLog` 和 `traceDetails`，前端链路追踪页展示详情阶段，请求日志页只提供主记录与深链。
- 数据安全：trace detail 服务统一脱敏、截断、hash、长度统计；二进制以引用和摘要形式记录。
- 生命周期：trace detail 支持默认全量、配置化采样、稳定 requestId 采样、TTL、定时清理和先归档摘要再删除。
- 来源限制：chat/resource trace metadata 已标明 payload 来源与 wire body 限制，避免把结构化摘要误认为 raw wire dump。
- 文档闭环：ADR、需求、审计报告、任务索引和 Done 任务之间已形成回链。

## 验证命令

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --no-daemon` 通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestTraceDetailServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --no-daemon` 通过。
- `bun run test src/features/traces/traces-page.test.tsx src/features/request-logs/request-logs-page.test.tsx` 通过。
- `bun run typecheck` 通过。
