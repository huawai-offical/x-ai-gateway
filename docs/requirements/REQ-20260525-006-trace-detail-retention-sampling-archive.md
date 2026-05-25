# REQ-20260525-006 请求详情追踪保留、采样与归档增强

状态：Done  
日期：2026-05-25  
上游来源：用户要求“继续补 TTL、采样、归档、metadataTruncated 等更加精确追踪的功能”。

## 背景

`REQ-20260525-005` 已经完成请求详情追踪，能够按 `requestId` 记录下游请求、canonical 请求、转换计划、上游请求、上游响应、下游响应和错误阶段，并在 Admin 链路追踪页展示详情。但当前 trace detail 仍存在保留策略和精确标记不足：

- trace detail 同步写入数据库，没有配置化 TTL 和清理入口。
- metadata 会按上限截断，但响应中没有独立 `metadataTruncated` 字段。
- 没有采样策略，高流量场景下可能写入过多详情快照。
- 没有归档摘要，过期清理后无法判断清理过多少数据、覆盖哪些时间段。

## 目标

- 为 trace detail 增加配置化启用开关、采样率、保留 TTL、清理批量和调度开关。
- 持久化层增加 payload 与 metadata 各自的长度、hash、截断、脱敏标记。
- 查询响应暴露 `metadataHash`、`metadataOriginalLength`、`metadataStoredLength`、`metadataTruncated`、`metadataRedacted`。
- 写入时支持按 `requestId` 稳定采样，避免同一请求的不同阶段被部分采样。
- 为每条 trace detail 写入 `expiresAt`，用于 TTL 清理。
- 增加归档摘要表，按清理批次记录归档窗口、条数、阶段计数和状态。
- 增加服务级清理入口与定时任务，先写归档摘要再删除已过期详情。

## 范围

- `request_trace_detail` schema 增强。
- 新增 trace detail archive summary schema。
- `GatewayProperties` 增加 `gateway.observability.trace-detail` 配置。
- `GatewayRequestTraceDetailService` 增强采样、TTL、metadata 精确标记与清理归档能力。
- Admin trace detail response 增加 metadata 精确字段。
- 单元测试覆盖采样、TTL、metadata 截断和归档清理。

## 非目标

- 不实现外部对象存储归档。
- 不在本轮新增归档管理 UI。
- 不对已有历史 trace detail 做批量回填。
- 不改变请求详情阶段枚举和现有 Admin trace 查询入口。

## 方案

1. 在 `request_trace_detail` 增加 `metadata_hash`、`metadata_original_length`、`metadata_stored_length`、`metadata_truncated`、`metadata_redacted`、`expires_at`。
2. 新增 `request_trace_detail_archive`，记录每次 TTL 清理归档摘要。
3. `GatewayRequestTraceDetailService` 从 `GatewayProperties.Observability.TraceDetail` 读取配置。
4. 采样使用 `requestId` 的稳定 hash，确保一个请求的所有阶段同时保留或同时跳过。
5. 默认采样率为 `1.0`，默认 TTL 为 `P7D`，默认定时清理开启，避免默认行为丢失现有审计能力。
6. 清理任务按 `expiresAt < now` 分批查询，归档摘要记录即将删除的记录数量、最早/最晚创建时间和阶段分布，然后删除该批记录。
7. Admin trace detail response 同步返回 metadata 精确字段，前端可据此展示更准确的截断状态。

## 风险

- 采样如果配置过低会减少审计完整性，因此默认必须保留全量。
- TTL 清理属于破坏性数据生命周期动作，必须只删除已过期数据，并保留归档摘要。
- schema 变更需要兼容已有记录；新增字段需要默认值或 nullable 迁移口径。
- 定时任务运行频率和批量大小需要可配置，避免清理任务压垮数据库。

## 验收标准

- trace detail 写入后包含 `expiresAt`。
- metadata 被截断时 `metadataTruncated=true`，payload 截断仍由 `truncated=true` 表示。
- metadata 脱敏时 `metadataRedacted=true`，整体 `redacted` 仍兼容表示 payload 或 metadata 任一被脱敏。
- 配置采样率为 `0` 时不写入 trace detail；默认采样率为 `1` 时保持现有全量记录。
- TTL 清理会生成归档摘要并删除过期 trace detail，不删除未过期数据。
- 后端定向测试通过。

## 测试边界

- 后端 service 单测覆盖采样、TTL、metadata 精确字段和归档清理。
- 后端 query/controller 测试覆盖 response 新字段。
- 本轮不做真实上游请求 smoke。

## 关联任务

- `tasks/done/TASK-20260525-006-trace-detail-retention-sampling-archive-parent.md`

## 关联决策

- [ADR-0011 请求日志主记录与请求详情追踪拆表](../decisions/ADR-0011-request-log-trace-detail-split.md)

## 实现结果

- 新增 `request_trace_detail` metadata 精确字段：`metadataHash`、`metadataOriginalLength`、`metadataStoredLength`、`metadataTruncated`、`metadataRedacted`。
- 新增 `expiresAt`，trace detail 写入时按保留 TTL 计算过期时间。
- 新增 `request_trace_detail_archive`，用于保存 TTL 清理批次的归档摘要。
- `GatewayProperties` 和 `application.yaml` 增加 `gateway.observability.trace-detail` 配置：启用开关、采样率、TTL、payload/metadata 上限、清理开关、清理间隔和批量大小。
- `GatewayRequestTraceDetailService` 增加稳定 requestId 采样、TTL 写入、metadata 精确标记和过期归档清理。
- Admin trace response 返回 metadata 精确字段和过期时间。
- 前端链路追踪页展示 metadata hash、metadata 长度、metadata 脱敏/截断状态和过期时间。

## 验证情况

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestTraceDetailServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests" --no-daemon` 通过。
- `cd web; bun run test src/features/traces/traces-page.test.tsx` 通过。
- `cd web; bun run typecheck` 通过。

## 遗留问题与后续建议

- 归档当前保存的是清理摘要，不保存外部对象存储正文；如需长期留存被清理正文，需要单独设计对象存储归档。
- 清理任务按批次删除过期详情，后续可增加 Admin 归档查询 UI。
- 历史 trace detail 不做回填，新增字段从本次迁移后的写入开始完整。
