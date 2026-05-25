# TASK-20260525-005 请求详情追踪与转换审计父任务

## 类型

父任务 / task spec

## 背景

用户要求网关能够跟踪到请求详情，明确关心下游请求内容、上游返回内容以及中间转换过程。现有请求日志和链路追踪只记录元数据、选路摘要、缓存命中和 usage，不足以支撑完整请求详情审计。

## 目标

- 建立以 `requestId` 为主线的请求详情快照表。
- 在 chat 与 resource 执行链路记录关键阶段。
- Admin trace 查询透出详情阶段。
- 前端链路追踪展示请求详情阶段。
- 所有正文默认脱敏、截断并记录 hash 和长度。
- 统计最近窗口下总体、Provider 和每个凭证的可用率、成功率、失败率、取消率、平均耗时与最近成功/失败时间。
- 在监控运维界面展示总体健康统计和凭证健康表。

## 非目标

- 不实现请求 replay。
- 不暴露密钥明文、Authorization、Cookie、OAuth token 或完整二进制文件。
- 不引入对象存储。
- 不把完整原始 HTTP wire dump 作为本轮硬目标。

## 上游来源

- `docs/requirements/REQ-20260525-005-request-trace-detail-audit.md`

## 子任务

- `TASK-20260525-005-01-request-trace-detail-backend.md`
- `TASK-20260525-005-02-request-trace-detail-frontend.md`
- `TASK-20260525-005-03-observability-health-metrics.md`

## 输入

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/observability/`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/ObservabilityQueryService.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/ObservabilityTraceResponse.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/GatewayChatExecutionService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayResourceExecutionService.java`
- `src/main/resources/db/changelog/`
- `web/src/features/traces/`
- `web/src/features/request-logs/`
- `web/src/features/ops/`

## 输出

- `request_trace_detail` 持久化结构。
- 请求详情记录与查询 API。
- chat/resource 阶段快照。
- 前端链路追踪详情展示。
- `GET /admin/observability/health` 窗口健康统计 API。
- 运维总览总体健康指标与凭证健康表。
- 定向测试与文档任务回写。

## 影响范围

- Admin 可观测性 API。
- 网关请求执行热路径。
- 请求日志和链路追踪 UI。
- 运维总览 UI。
- 数据库 schema。

## 依赖

- 现有 `request_log`、`route_decision_log` 和 trace 查询服务。
- 现有 ObjectMapper、Liquibase、JPA repository。
- 现有前端 typed query 与 trace 页面。

## 风险

- 记录详情可能增加热路径写入压力。
- payload 中可能有敏感数据，脱敏逻辑必须保守。
- streaming 与 SDK runtime 无法保证拿到完整 wire-level 原始包，本轮记录网关层可获得的真实结构化快照。
- 工作区已有大量并行改动，必须避免回滚无关文件。

## 验收标准

- `GET /admin/observability/traces/{requestId}` 返回 `traceDetails`。
- `GET /admin/observability/health` 返回总体、Provider、凭证维度最近窗口健康统计。
- 请求详情阶段可按 `createdAt/id` 稳定排序。
- trace detail 默认脱敏、截断并包含 hash/长度标记。
- chat/resource 请求成功和失败路径至少记录关键阶段。
- 前端链路追踪页能展示 trace detail。
- 运维总览页能展示总体成功率、可用率和凭证健康表。
- 定向后端与前端测试通过。

## 测试边界

- 后端定向测试覆盖 trace detail service、query response 和关键执行链路。
- 前端定向测试覆盖 trace detail 渲染、请求日志深链和运维健康统计。
- 不默认消耗真实 provider key。

## 关联文档

- `docs/requirements/REQ-20260525-005-request-trace-detail-audit.md`
- `docs/decisions/ADR-0011-request-log-trace-detail-split.md`

## 当前状态

Done

## 进度记录

- 2026-05-25：创建需求和父任务，准备进入后端 trace detail 持久化与查询实现。
- 2026-05-25：用户追加凭证最近可用率/成功率和运维总体统计诉求，新增健康统计子任务并明确窗口统计口径。
- 2026-05-25：完成 trace detail 持久化、chat/resource 执行链路阶段记录、Admin trace 查询透出、前端 trace/request-log 展示、健康统计 API 与运维总览展示。

## 实现结果

- 后端完成 `request_trace_detail`、`GatewayRequestTraceDetailService`、trace detail 查询映射和相关 DTO。
- chat/resource 执行链路记录下游请求、canonical 请求、转换计划、上游请求、上游响应、下游响应和错误阶段。
- 前端链路追踪页展示 trace detail，请求日志页提供跳转入口。
- 运维健康统计 API 和运维总览凭证健康表完成。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestTraceDetailServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --no-daemon` 通过。
- `cd web; bun run test src/features/ops/ops-page.test.tsx src/features/traces/traces-page.test.tsx src/features/request-logs/request-logs-page.test.tsx` 通过。
- `cd web; bun run typecheck` 通过。

## 遗留问题

- TTL、采样、归档摘要和 metadata 精确字段已由 `TASK-20260525-006` 补齐。
- 暂未实现对象存储正文归档；二进制、文件、图片、音频和视频仍只保存结构化引用、大小和类型。
