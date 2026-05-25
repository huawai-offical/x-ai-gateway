# TASK-20260525-006 请求详情追踪保留、采样与归档增强父任务

## 类型

父任务 / task spec

## 背景

请求详情追踪已经具备记录和展示能力，但还缺少生产级数据生命周期控制：TTL、采样、归档摘要和 metadata 精确截断标记。用户明确要求继续补齐这些能力。

## 目标

- 增加 trace detail 配置化采样和 TTL。
- 增加 metadata hash、长度、截断、脱敏字段。
- 增加 trace detail 归档摘要和过期清理服务。
- 保持默认全量记录，不因新增采样导致现有能力默认丢失。
- 补充后端定向测试。

## 非目标

- 不新增归档管理前端页面。
- 不接入对象存储。
- 不做历史数据回填。

## 上游来源

- `docs/requirements/REQ-20260525-006-trace-detail-retention-sampling-archive.md`

## 子任务

- `TASK-20260525-006-01-trace-detail-schema-config.md`
- `TASK-20260525-006-02-trace-detail-sampling-retention-service.md`
- `TASK-20260525-006-03-trace-detail-archive-cleanup-tests.md`

## 输入

- `GatewayRequestTraceDetailService`
- `RequestTraceDetailEntity`
- `RequestTraceDetailRepository`
- `GatewayProperties`
- Liquibase changelog
- Observability trace response mapping

## 输出

- 增强后的 trace detail schema。
- trace detail archive summary schema。
- 配置化采样和 TTL。
- metadata 精确字段。
- 归档清理服务和测试。

## 影响范围

- 网关请求详情写入热路径。
- Admin trace 查询响应结构。
- 数据库迁移。
- 后台定时任务。

## 依赖

- 现有 `request_trace_detail`。
- Spring `@Scheduled`。
- GatewayProperties 配置绑定。

## 风险

- 采样配置误用可能降低审计覆盖率。
- TTL 清理会删除详情正文，必须先写归档摘要。
- 新增字段需要兼容旧数据。

## 验收标准

- metadata 截断/脱敏字段可单独查询。
- 采样率配置生效并按 requestId 稳定。
- 过期记录清理前写入归档摘要。
- 未过期记录不会被删除。
- 定向测试通过。

## 测试边界

- Service unit tests。
- Repository/API mapping 编译与 query tests。
- 不做真实 provider smoke。

## 关联文档

- `docs/requirements/REQ-20260525-006-trace-detail-retention-sampling-archive.md`
- `docs/decisions/ADR-0011-request-log-trace-detail-split.md`

## 当前状态

Done

## 进度记录

- 2026-05-25：创建父任务，准备拆分 schema/config、service、archive/tests 三个子任务。
- 2026-05-25：完成 schema/config、采样 TTL 写入、metadata 精确字段、归档清理、前端展示和定向测试。

## 实现结果

- trace detail schema 增加 metadata 精确字段和 `expiresAt`。
- 新增 trace detail archive summary 表、entity 和 repository。
- trace detail service 支持配置化采样、TTL、清理批量和定时清理。
- Admin trace response 和前端展示支持 metadata 精确字段。

## 验证结果

- 后端定向测试通过：`GatewayRequestTraceDetailServiceTests`、`ObservabilityQueryServiceTests`、`ObservabilityAdminControllerTests`。
- 前端定向测试通过：`traces-page.test.tsx`。
- 前端类型检查通过：`bun run typecheck`。

## 遗留问题

- 暂未实现外部对象存储归档和归档查询 UI。
