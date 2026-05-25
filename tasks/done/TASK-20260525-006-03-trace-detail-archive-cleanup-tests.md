# TASK-20260525-006-03 Trace Detail 归档清理与测试

## 类型

子任务 / task spec

## 背景

trace detail 增加 TTL 后，需要有可靠的清理入口，并在删除详情正文前留下归档摘要，避免审计数据被无痕删除。

## 目标

- 新增归档摘要 entity/repository。
- 增加清理服务方法和定时任务。
- 清理时先写归档摘要，再删除过期详情。
- 测试覆盖过期删除、未过期保留和归档摘要。

## 非目标

- 不实现外部归档存储。
- 不新增 Admin UI。

## 上游来源

- `docs/requirements/REQ-20260525-006-trace-detail-retention-sampling-archive.md`
- `TASK-20260525-006-trace-detail-retention-sampling-archive-parent.md`

## 输入

- `RequestTraceDetailRepository`
- 新增 archive repository
- `GatewayRequestTraceDetailService`

## 输出

- 归档摘要持久化。
- 过期清理方法与调度。
- 定向测试。

## 影响范围

- 后台定时任务。
- trace detail 数据生命周期。

## 依赖

- schema/config 子任务。
- sampling/retention service 子任务。

## 风险

- 删除操作必须限定在过期数据。
- 大批量清理需要可配置 batch size。

## 验收标准

- 已过期记录被删除。
- 未过期记录不删除。
- 每次清理写入归档摘要，包含数量、窗口和 stage 分布。

## 测试边界

- service unit tests。
- 后端定向测试。

## 当前状态

Done

## 实现结果

- 新增 TTL 清理入口 `archiveAndDeleteExpiredTraceDetails`。
- 定时任务按 `gateway.observability.trace-detail.cleanup-interval` 执行。
- 清理时先写归档摘要，再批量删除过期 trace detail。
- 测试覆盖归档摘要和批量删除。

## 验证结果

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.observability.GatewayRequestTraceDetailServiceTests" --no-daemon` 通过。

## 遗留问题

- 归档摘要暂未提供 Admin 查询接口。
