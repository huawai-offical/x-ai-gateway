# REQ-20260525-005 请求详情追踪与转换审计

状态：Done  
日期：2026-05-25  
上游来源：用户提出“需要能够跟踪到请求详情”，并明确希望看到“下游请求了什么、上游返回了什么、中间做了什么转换”。

## 背景

当前系统已经具备 `request_log`、选路决策、缓存命中、usage 和链路追踪页，但这些数据主要是请求元数据和运行摘要，不能完整回答单个请求在网关中的细节流转：下游原始请求、网关规范化请求、转换计划、上游请求、上游响应以及最终下游响应分别是什么。

在“多厂商 native API 网关 + 可翻译能力硬失败”的新定义下，网关不仅需要告诉管理员请求成功或失败，还需要能解释一次请求为什么这样选路、怎样转换、哪些内容被发送到上游、上游实际返回了什么，以及最终返回给下游前是否经过规范化。

## 目标

- 为每个网关请求按同一个 `requestId` 记录可审计的请求详情快照。
- 至少覆盖下游入口请求、网关规范化请求、选路与转换计划、上游执行请求摘要、上游响应或错误摘要、下游响应摘要。
- 请求详情需要可在 Admin 链路追踪和请求日志入口中查询。
- 所有正文快照必须默认脱敏、截断，并记录 hash、原始长度、存储长度、是否脱敏、是否截断。
- 对无法直接记录的二进制、文件、图片、音频、视频等内容，记录结构化引用、大小和类型，不写入完整二进制正文。
- 基于最近时间窗口统计总体、Provider 和凭证维度的可用率、成功率、失败率、取消率、平均耗时与最近成功/失败时间。
- 监控运维界面需要展示总体健康统计，并能下钻看到每个凭证最近窗口的健康表现。

## 范围

- 后端观测持久化模型和 Liquibase 迁移。
- 后端 trace detail 记录服务、脱敏与截断逻辑。
- chat 和功能性 resource 执行链路的关键阶段接入。
- Admin trace 查询响应结构。
- 前端请求日志/链路追踪页展示请求详情。
- Admin 观测健康统计 API。
- 运维总览页总体健康指标和凭证健康表。

## 非目标

- 不实现完整 replay 或重放执行。
- 不把密钥、Authorization、Cookie、完整 OAuth token、完整文件二进制写入数据库。
- 不在本轮接入对象存储保存超大 payload。
- 不保证 Spring AI 底层 SDK 能暴露完整 native HTTP 原始包；本轮先记录网关可构造和可接收的真实结构化快照。
- 不为用户侧普通 Portal 暴露完整请求详情。

## 方案

1. 新增 `request_trace_detail` 表，以 `request_id` 串联请求详情阶段。
2. 新增 `GatewayRequestTraceDetailService`，提供按阶段记录 JSON/对象/错误的统一入口。
3. 服务统一执行脱敏、截断、hash、长度统计和 metadata 包装。
4. chat 执行链路在过滤后记录 canonical 请求和转换计划，在候选执行前记录上游请求摘要，在成功/失败后记录上游响应或错误摘要，返回前记录下游响应摘要。
5. resource 执行链路记录 resource 请求、转换计划、上游 payload、上游响应和下游响应摘要。
6. Admin trace 查询响应增加 `traceDetails`，前端按阶段展示。
7. 复用 `request_log` 的最近窗口数据生成健康统计：成功率 = `COMPLETED / total`，可用率 = `1 - FAILED / total`，取消率单独展示，避免把客户端中断误判为上游不可用。
8. 健康统计按总体、Provider 和凭证聚合；凭证项展示 `credentialId`、provider、可识别标签、请求量、成功率、可用率、失败率、取消率、平均耗时和最近成功/失败时间。

## 存储边界决策

- `request_log` 保持请求主记录和统计表定位，只保存轻量元数据、状态、路由摘要、耗时、错误和健康统计所需字段。
- `request_trace_detail` 保存上下游请求内容、响应摘要和转换过程快照，通过 `request_id` 关联 `request_log`。
- Admin API 和前端可以聚合展示一条“请求详情”，但数据库层不把完整 payload 合并进 `request_log`。
- 该决策已固化到 [ADR-0011](../decisions/ADR-0011-request-log-trace-detail-split.md)。

## 风险

- 请求正文可能包含敏感业务数据，必须默认脱敏和截断。
- 记录详情会增加数据库写入量，当前已补充 TTL、采样和归档摘要；如需长期留存完整正文，仍需单独设计对象存储策略。
- 部分 provider runtime 通过 SDK 调用，无法无侵入拿到真正 HTTP wire body；本轮记录网关构造出的上游请求摘要和 SDK 返回结构摘要。
- streaming 响应如果全量记录会放大存储压力，本轮记录 final/partial 结构化摘要。

## 验收标准

- 通过 `requestId` 查询 trace 时，响应包含请求详情阶段列表。
- trace detail 至少能看到下游入口/规范化请求、转换计划、上游请求摘要、上游响应或错误摘要、下游响应摘要。
- 所有 trace detail 均带有 hash、长度、截断和脱敏标记。
- 前端链路追踪页能展示请求详情阶段，且请求日志入口能深链到链路追踪。
- 后端定向测试覆盖脱敏、截断、trace 查询透出。
- 前端定向测试覆盖 trace detail 渲染。
- `GET /admin/observability/health` 支持最近窗口统计，并返回总体、Provider 和凭证维度健康指标。
- 运维总览页能展示总体成功率、可用率、失败率、平均耗时，以及每个凭证最近窗口健康统计。

## 测试边界

- 后端：trace detail 服务、Observability 查询响应、chat/resource 关键阶段接入的单元或集成测试。
- 前端：`traces` 和 `request-logs` 页面相关测试。
- 前端：运维页健康统计渲染测试。
- 不默认执行真实上游请求；如后端中转站和真实 key 可用，可后续补一条低成本人工 smoke。

## 关联任务

- `tasks/done/TASK-20260525-005-request-trace-detail-audit-parent.md`

## 实现结果

- 新增 `request_trace_detail` 持久化模型、repository、Liquibase 迁移和 Admin trace detail 响应。
- 新增 `GatewayRequestTraceDetailService`，统一处理 trace detail 记录、脱敏、截断、hash、长度和 metadata。
- chat 执行链路接入下游请求、canonical 请求、转换计划、上游请求、上游响应、下游响应和错误阶段。
- resource 功能性 API 执行链路接入 JSON、二进制 JSON、multipart 的请求详情追踪，覆盖 resource/image/file 等非 chat 通道的可审计快照。
- `GET /admin/observability/traces/{requestId}` 返回 `traceDetails`，前端链路追踪页展示阶段、方向、payload、metadata、hash、长度、脱敏和截断标记。
- 请求日志页提供链路追踪深链，便于从单次请求进入详情排障。
- 新增 `GET /admin/observability/health`，基于最近窗口聚合总体、Provider 和凭证维度的成功率、可用率、失败率、取消率、平均耗时、最近成功/失败时间。
- 运维总览页新增总体健康指标和凭证最近窗口健康表。

## 验证情况

- 通过后端定向测试：
  `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestTraceDetailServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.ObservabilityQueryServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ObservabilityAdminControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --no-daemon`
- 通过前端定向测试：
  `bun run test src/features/ops/ops-page.test.tsx src/features/traces/traces-page.test.tsx src/features/request-logs/request-logs-page.test.tsx`
- 通过前端类型检查：
  `bun run typecheck`

## 遗留问题与后续建议

- 二进制、文件、图片、音频和视频只记录结构化引用、大小和类型，不保存完整正文；如需深度审计，可另行设计对象存储留痕。
- TTL、采样、归档摘要和 metadata 精确截断/脱敏字段已由 `REQ-20260525-006` 补齐。
