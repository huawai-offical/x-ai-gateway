# TASK-20260525-005-01 请求详情追踪后端

## 类型

子任务 / task spec

## 背景

请求详情需要后端事实源支撑。现有 `request_log` 不保存请求/响应 payload，也没有按阶段记录转换计划和上游执行摘要。

## 目标

- 新增 trace detail entity、repository、response 和查询映射。
- 新增统一记录服务，负责脱敏、截断、hash 和长度统计。
- 扩展 `ObservabilityTraceResponse` 返回 trace detail 列表。
- 在 chat/resource 执行链路记录关键阶段。
- 增加 Liquibase 迁移。

## 非目标

- 不改造所有 provider SDK 内部 HTTP client。
- 不记录完整文件二进制。
- 不实现采样/TTL UI。

## 上游来源

- `docs/requirements/REQ-20260525-005-request-trace-detail-audit.md`
- `tasks/in-progress/TASK-20260525-005-request-trace-detail-audit-parent.md`

## 输入

- 后端观测服务与执行链路。
- Liquibase changelog。
- Observability Admin API。

## 输出

- `request_trace_detail` 表。
- `GatewayRequestTraceDetailService`。
- `RequestTraceDetailResponse`。
- 扩展后的 trace 查询响应。
- 后端测试。

## 影响范围

- 网关热路径观测写入。
- Admin trace 查询。
- 数据库迁移。

## 依赖

- ObjectMapper。
- RequestLog 与 Observability 查询服务。
- 当前 JPA/Liquibase 约定。

## 风险

- payload 写入量和敏感信息泄露风险。
- 异步队列暂不覆盖 trace detail，本轮同步写入需要保持快照小且失败不阻断请求。

## 验收标准

- trace detail 记录失败不会中断主请求。
- payload 包含 secret/token/authorization 时被脱敏。
- 超长 payload 被截断并标记。
- trace 查询能返回按时间排序的 trace detail。

## 测试边界

- Service unit tests。
- Observability query/controller tests。
- 必要执行链路 tests。

## 当前状态

Done

## 实现结果

- 新增 `request_trace_detail` 表、entity、repository 和 Liquibase 迁移。
- 新增 `GatewayRequestTraceDetailService`，记录 payload、metadata、hash、原始长度、存储长度、脱敏和截断标记。
- `ObservabilityTraceResponse` 增加 `traceDetails`，`ObservabilityQueryService` 按 `createdAt/id` 查询并映射详情阶段。
- `GatewayChatExecutionService` 接入 chat 请求详情追踪。
- `GatewayResourceExecutionService` 接入 JSON、二进制 JSON、multipart 功能性 API 请求详情追踪。
- 新增/更新后端测试覆盖 trace detail service 脱敏截断、trace 查询、健康统计和执行链路编译回归。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestTraceDetailServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --no-daemon` 通过。

## 遗留问题

- metadata 截断仅通过 metadata 存储长度可间接判断，顶层 `truncated` 当前仍表示 payload 是否截断。
- 暂未实现异步写入、采样、TTL 或对象存储归档。
