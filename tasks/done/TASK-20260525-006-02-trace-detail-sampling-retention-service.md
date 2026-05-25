# TASK-20260525-006-02 Trace Detail 采样与 TTL 写入服务

## 类型

子任务 / task spec

## 背景

请求详情追踪在高流量下可能产生大量数据，需要通过配置控制采样和保留期限，同时保持默认全量记录。

## 目标

- `GatewayRequestTraceDetailService` 支持启用开关、采样率和 TTL。
- 按 requestId 稳定采样。
- 写入时计算 payload 与 metadata 各自 hash、长度、截断和脱敏状态。
- 写入 `expiresAt`。

## 非目标

- 不实现清理归档。
- 不改变调用方 trace 阶段。

## 上游来源

- `docs/requirements/REQ-20260525-006-trace-detail-retention-sampling-archive.md`
- `TASK-20260525-006-trace-detail-retention-sampling-archive-parent.md`

## 输入

- `GatewayRequestTraceDetailService`
- `GatewayProperties`

## 输出

- 可配置采样与 TTL 写入逻辑。
- 单测覆盖。

## 影响范围

- 请求详情写入热路径。

## 依赖

- schema/config 子任务。

## 风险

- 采样必须按 requestId 稳定，避免同一请求阶段不完整。

## 验收标准

- 默认全量写入。
- sampling-rate 为 0 时跳过写入。
- metadata 截断/脱敏独立标记正确。
- expiresAt 按 TTL 写入。

## 测试边界

- `GatewayRequestTraceDetailServiceTests`。

## 当前状态

Done

## 实现结果

- `GatewayRequestTraceDetailService` 默认全量记录，支持 `samplingRate` 采样。
- 采样按 `requestId` 稳定 hash，避免同一请求阶段被拆散。
- 写入时分别计算 payload 与 metadata 的 hash、长度、截断和脱敏状态。
- 写入时按 `retentionTtl` 设置 `expiresAt`。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestTraceDetailServiceTests" --no-daemon` 通过。
