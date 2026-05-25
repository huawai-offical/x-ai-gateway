# ADR-0011 请求日志主记录与请求详情追踪拆表

状态：Accepted  
日期：2026-05-25  
关联需求：[REQ-20260525-005](../requirements/REQ-20260525-005-request-trace-detail-audit.md)、[REQ-20260525-006](../requirements/REQ-20260525-006-trace-detail-retention-sampling-archive.md)

## 背景

用户要求系统能够审计每个请求的详情，包括下游请求内容、上游返回内容，以及网关中间做过的转换。系统已有 `request_log` 表，但它主要承载请求主记录、路由摘要、状态、耗时、错误和健康统计所需字段，不适合直接保存完整上下游 payload。

请求详情正文具有明显不同的数据特征：

- 体积更大，可能包含业务内容、提示词、工具参数、文件引用和上游响应摘要。
- 敏感度更高，必须默认脱敏、截断并记录 hash 与长度。
- 生命周期更短，需要采样、TTL、清理和归档摘要。
- 查询路径不同，列表和统计通常只需要主记录，排障详情才需要加载阶段快照。

## 决策

请求日志采用物理拆表设计：

- `request_log` 作为请求主记录表，只保存轻量元数据、状态、路由摘要、耗时、错误信息和统计所需字段。
- `request_trace_detail` 作为请求详情表，通过 `request_id` 关联 `request_log`，按阶段记录下游请求、canonical 请求、转换计划、上游请求、上游响应、下游响应和错误摘要。
- `request_trace_detail_archive` 作为清理归档摘要表，记录 TTL 清理批次、过期窗口、清理条数、阶段计数和状态。
- Admin API 和前端页面可以把三者聚合展示成一个“请求详情”，但数据库层不把完整 payload 合并进 `request_log`。

## 影响

正向影响：

- 请求日志列表、健康统计和成功率/可用率聚合继续依赖轻量 `request_log`，避免被大字段拖慢。
- 请求详情可以独立配置采样率、TTL、payload/metadata 上限和清理批量。
- 敏感正文的脱敏、截断、hash、长度和过期策略不污染主日志模型。
- 后续如果接入对象存储归档，可以只扩展 trace detail 生命周期，不破坏请求日志统计模型。

代价：

- 查询一次完整请求详情需要聚合 `request_log` 与 `request_trace_detail`。
- 数据库迁移和 API DTO 需要维护两个层次的字段。
- 历史 `request_log` 不会自动拥有请求详情阶段，只有接入 trace detail 后的新请求具备完整详情。

## 约束

- 不允许把密钥、Authorization、Cookie、完整 OAuth token 或完整二进制正文写入 `request_trace_detail`。
- `request_trace_detail` 默认全量采样，但必须支持配置采样率，且同一个 `requestId` 的所有阶段必须稳定保留或稳定跳过。
- `request_trace_detail` 必须写入 `expiresAt`，过期清理前必须先写入 `request_trace_detail_archive` 摘要。
- 如果某个阶段无法获得真实 HTTP wire body，应记录网关可构造或可接收的结构化摘要，并在 metadata 中标明来源和限制。

## 闭环证据

- 2026-05-26 已通过 [REP-20260525-008](../reports/REP-20260525-008-adr0011-request-log-trace-detail-split-closure-audit.md) 和 [TASK-20260525-008](../../tasks/done/TASK-20260525-008-adr0011-request-log-trace-detail-split-closure.md) 完成逐项审计。
- 当前实现确认 `request_log` 只承载请求主记录和健康统计字段；完整上下游 payload 与阶段 metadata 由 `request_trace_detail` 承接，TTL 清理摘要由 `request_trace_detail_archive` 承接。
- `GatewayChatExecutionService` 与 `GatewayResourceExecutionService` 已在 trace metadata 中写入 `payloadSource`、`wireBody` 和 `wireBodyLimitation`，明确区分下游 parsed body、网关结构化摘要和非 raw upstream HTTP wire body。
- 闭环验证覆盖后端 trace detail service、Admin trace query/controller、chat/resource 执行链路 metadata 标记，以及前端链路追踪和请求日志页面。

## 后续

- 如需要长期保留完整正文，应单独设计对象存储归档，不应把正文永久堆积在 `request_log`。
- 如需要 SQL 侧统一查看，可以新增只读 view 或 Admin 聚合 API，不改变物理拆表决策。
