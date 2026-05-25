# TASK-20260525-006-01 Trace Detail Schema 与配置增强

## 类型

子任务 / task spec

## 背景

trace detail 目前只有 payload 维度的长度和截断字段，缺少 metadata 独立字段，也没有 TTL 所需 `expiresAt` 和配置入口。

## 目标

- 为 `request_trace_detail` 增加 metadata 精确字段和 `expires_at`。
- 新增 `request_trace_detail_archive`。
- 在 `GatewayProperties` 和 `application.yaml` 增加 trace detail 配置。
- 扩展 Admin trace detail response。

## 非目标

- 不实现清理逻辑。
- 不实现前端页面。

## 上游来源

- `docs/requirements/REQ-20260525-006-trace-detail-retention-sampling-archive.md`
- `TASK-20260525-006-trace-detail-retention-sampling-archive-parent.md`

## 输入

- Entity、Repository、DTO、Liquibase、配置文件。

## 输出

- Schema 与 Java model 增强。
- 配置绑定字段。

## 影响范围

- 数据库迁移。
- Admin trace response。

## 依赖

- 现有 trace detail 表和查询映射。

## 风险

- 新字段需要兼容旧记录。

## 验收标准

- 编译通过。
- response 包含 metadata 精确字段。

## 测试边界

- 编译和 Observability query tests。

## 当前状态

Done

## 实现结果

- 新增 `db.changelog-0010-request-trace-detail-retention.yaml` 并接入 master changelog。
- `RequestTraceDetailEntity` 增加 metadata 精确字段和 `expiresAt`。
- 新增 `RequestTraceDetailArchiveEntity` 与 repository。
- `GatewayProperties` 和 `application.yaml` 增加 trace detail 配置。
- Admin `RequestTraceDetailResponse` 增加 metadata 精确字段。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests" --no-daemon` 通过。
