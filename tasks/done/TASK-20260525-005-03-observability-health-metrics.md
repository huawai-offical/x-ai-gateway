# TASK-20260525-005-03 凭证与运维健康统计

## 类型

子任务 / task spec

## 背景

用户要求统计每个凭证最近的可用率、成功率等健康指标，并在监控运维界面统计总体可用率、成功率等指标。现有凭证累计计数能展示长期用量，但不能回答“最近窗口是否健康”；现有运维页也主要展示 QPS、缓存、Token、SLO 和容量压力，缺少按凭证聚合的近期成功/可用状态。

## 目标

- 基于 `request_log` 最近时间窗口聚合健康指标。
- 返回总体、Provider 和凭证维度统计。
- 成功率定义为 `COMPLETED / total`。
- 可用率定义为 `1 - FAILED / total`，取消请求单独展示，避免误判为上游不可用。
- 运维总览展示总体成功率、可用率、失败率、平均耗时与凭证健康表。

## 非目标

- 不新增独立健康统计表。
- 不做长期时序报表或图表导出。
- 不消耗真实 provider key 做主动探测。
- 不改变 `GatewayRequestLifecycleService` 现有累计计数写回逻辑。

## 上游来源

- `docs/requirements/REQ-20260525-005-request-trace-detail-audit.md`
- `tasks/in-progress/TASK-20260525-005-request-trace-detail-audit-parent.md`

## 输入

- `request_log` 最近窗口数据。
- `UpstreamCredentialEntity` 中的凭证名称和 provider 信息。
- 运维总览页当前选择的时间窗口。

## 输出

- `GET /admin/observability/health` API。
- 总体、Provider、凭证维度健康 DTO。
- 运维总览健康指标和凭证健康表。
- 后端与前端定向测试。

## 影响范围

- Admin Observability API。
- 运维总览 UI。
- `RequestLogRepository` 查询能力。
- `ObservabilityQueryService` 聚合逻辑。

## 依赖

- `RequestLogRepository`。
- `UpstreamCredentialRepository`。
- 现有前端 `opsApi`、`OpsPage` 和 typed query。

## 风险

- 默认窗口过大时可能扫描较多 `request_log` 数据，本轮默认 24 小时并支持显式 `from/to`。
- 取消请求的含义可能来自客户端中断，不直接等价于上游失败，因此必须单独展示。
- 凭证名称可能为空或已删除，返回需要保留 `credentialId` 和 provider 作为兜底标签。

## 验收标准

- 未传 `from/to` 时 API 默认统计最近 24 小时。
- API 返回 `sampledFrom`、`sampledTo`、`total`、`providers`、`credentials`。
- 总体、Provider、凭证的成功率、可用率、失败率、取消率和平均耗时计算正确。
- 运维页展示总体成功率、可用率、失败率、平均耗时和凭证健康表。
- 后端定向测试和前端 ops 页面测试通过。

## 测试边界

- 后端：`ObservabilityQueryServiceTests` 覆盖健康聚合口径。
- 后端：`ObservabilityAdminControllerTests` 覆盖 `/admin/observability/health` 路由。
- 前端：`web/src/features/ops/ops-page.test.tsx` 覆盖健康统计渲染。

## 关联文档

- `docs/requirements/REQ-20260525-005-request-trace-detail-audit.md`

## 关联任务

- `TASK-20260525-005-request-trace-detail-audit-parent.md`
- `TASK-20260525-005-01-request-trace-detail-backend.md`
- `TASK-20260525-005-02-request-trace-detail-frontend.md`

## 当前状态

Done

## 实现结果

- `RequestLogRepository` 增加最近窗口健康查询能力。
- `ObservabilityQueryService` 聚合总体、Provider、凭证维度健康指标。
- 新增 `HealthMetricResponse`、`ProviderHealthMetricResponse`、`CredentialHealthMetricResponse` 和 `ObservabilityHealthResponse`。
- `ObservabilityAdminController` 新增 `GET /admin/observability/health`。
- 运维总览页新增总体成功率、可用率、失败率、平均耗时和凭证最近窗口健康表。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests" --no-daemon` 通过。
- `cd web; bun run test src/features/ops/ops-page.test.tsx` 通过。
- `cd web; bun run typecheck` 通过。

## 遗留问题

- 当前为按需查询 `request_log` 最近窗口，后续若日志量继续增长，应补索引审计、缓存或周期性聚合表。
