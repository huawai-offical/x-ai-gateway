# TASK-20260525-008 ADR-0011 请求日志与请求详情拆表闭环

## 类型

父任务 / task spec

## 背景

ADR-0011 已经确立 `request_log` 主记录、`request_trace_detail` 请求详情和 `request_trace_detail_archive` 归档摘要的拆表设计。用户要求闭环该 ADR，需要用当前代码、schema、API、UI 和测试逐项证明决策已经落地，并补齐发现的实现证据缺口。

## 目标

- 完成 ADR-0011 逐项审计。
- 确认 `request_log` 不保存完整 payload。
- 确认 `request_trace_detail` 与 archive 表、实体、repository、服务和查询链路完整。
- 为无法代表真实 HTTP wire body 的 trace detail 阶段补充 metadata 来源与限制说明。
- 增加测试覆盖并回写文档。

## 非目标

- 不合并 `request_log` 与 `request_trace_detail`。
- 不新增对象存储归档。
- 不新增归档查询 UI。
- 不执行真实 provider smoke。

## 上游来源

- `docs/decisions/ADR-0011-request-log-trace-detail-split.md`
- `docs/reports/REP-20260525-008-adr0011-request-log-trace-detail-split-closure-audit.md`

## 输入

- `RequestLogEntity`
- `RequestTraceDetailEntity`
- `RequestTraceDetailArchiveEntity`
- `GatewayRequestTraceDetailService`
- `GatewayChatExecutionService`
- `GatewayResourceExecutionService`
- `ObservabilityQueryService`
- 前端 `traces` 和 `request-logs` 页面
- 相关 Liquibase changelog 和测试

## 输出

- ADR-0011 闭环审计报告。
- trace detail metadata 来源/限制标记。
- 后端与前端定向验证结果。
- 回写后的需求、决策和任务记录。

## 影响范围

- 请求详情追踪 metadata。
- chat/resource 执行链路 trace detail 记录。
- 后端定向测试。
- 本地文档和任务索引。

## 依赖

- `REQ-20260525-005`
- `REQ-20260525-006`
- 现有 trace detail schema 和服务。

## 风险

- metadata 字段如果不统一，管理员仍可能误以为 `UPSTREAM_*` payload 是完整原始 wire body。
- 热路径新增 metadata 不能引入重型序列化或阻断主请求。
- 工作区已有大量并行改动，必须避免回滚无关文件。

## 验收标准

- ADR-0011 每个约束都有当前状态证据。
- chat 和 resource trace detail 的 metadata 至少包含 payload 来源和 wire body 限制说明。
- 定向后端测试通过。
- 前端 trace 页面测试和类型检查通过。
- 文档和任务状态真实回写。

## 测试边界

- 后端：`GatewayRequestTraceDetailServiceTests`、`GatewayChatExecutionServiceTests`、`GatewayResourceExecutionServiceTests`、`ObservabilityQueryServiceTests`、`ObservabilityAdminControllerTests`。
- 前端：`traces-page.test.tsx`、`request-logs-page.test.tsx`、`typecheck`。
- 不做真实上游请求。

## 关联文档

- `docs/decisions/ADR-0011-request-log-trace-detail-split.md`
- `docs/reports/REP-20260525-008-adr0011-request-log-trace-detail-split-closure-audit.md`
- `docs/requirements/REQ-20260525-005-request-trace-detail-audit.md`
- `docs/requirements/REQ-20260525-006-trace-detail-retention-sampling-archive.md`

## 当前状态

Done

## 进度记录

- 2026-05-25：创建 ADR-0011 闭环审计任务，开始补齐 trace detail metadata 来源与限制证据。
- 2026-05-26：完成 chat/resource trace metadata 来源与 wire body 限制标记；新增后端测试覆盖 `UPSTREAM_REQUEST` 结构化摘要语义。
- 2026-05-26：ADR-0011 已补充闭环证据，回链审计报告与本 Done 任务。

## 实现结果

- `GatewayChatExecutionService` 的 trace metadata 统一写入 `payloadSource`、`wireBody`、`wireBodyLimitation`。
- `GatewayResourceExecutionService` 的 trace metadata 统一写入 `payloadSource`、`wireBody`、`wireBodyLimitation`。
- `UPSTREAM_REQUEST` 和 `UPSTREAM_RESPONSE` 明确标记为 gateway/runtime/executor 结构化摘要，不是 raw upstream HTTP wire body。
- `GatewayChatExecutionServiceTests` 增加 chat 上游请求 metadata 来源与限制断言。
- `GatewayResourceExecutionServiceTests` 增加 resource 上游请求 metadata 来源与限制断言。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --no-daemon` 通过。
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestTraceDetailServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --no-daemon` 通过。
- `bun run test src/features/traces/traces-page.test.tsx src/features/request-logs/request-logs-page.test.tsx` 通过。
- `bun run typecheck` 通过。

## 遗留问题

- 不新增对象存储正文归档；如未来需要长期保留完整正文，应另建需求和任务。
- 不新增归档查询 UI；当前归档表仅保存清理摘要。
