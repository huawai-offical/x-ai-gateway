# TASK-20260515-012 OpenAI Idempotency-Key TTL 与清理策略

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260514-030](../done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
上游来源：[TASK-20260515-009](../done/TASK-20260515-009-openai-idempotency-key-response-replay.md)

## 背景

`TASK-20260515-009` 已经把非流式 Chat/Responses 的 `Idempotency-Key` 响应持久化到 `openai_idempotency_record`。如果没有 TTL 与清理策略，长期真实 smoke 和压测会让该表持续增长，增加索引体积和重放查询成本。这个切片补齐 retention 基线，不改变幂等 replay 的语义。

## 目标

- 为 `openai_idempotency_record` 建立默认 24 小时 retention。
- 增加 repository 删除 `created_at` 早于 cutoff 的能力。
- 增加 service 手动清理 API，便于测试和后续 Admin/Ops 接入。
- 增加 scheduled cleanup，默认每小时执行一次。
- retention 与 cleanup interval 可通过 Spring 配置覆盖。

## 非目标

- 不增加 Admin UI 或公开 API。
- 不改变 `Idempotency-Key` 匹配维度和 request fingerprint 规则。
- 不清理仍在 retention 窗口内的记录。

## 输入

- `OpenAiIdempotencyReplayService`
- `OpenAiIdempotencyRecordRepository`
- `openai_idempotency_record.created_at`
- Spring scheduling 基线

## 输出

- `deleteByCreatedAtBefore` repository 方法。
- `purgeExpiredRecords(...)` service 方法。
- `@Scheduled` 定时清理入口。
- 单元测试覆盖 cutoff 计算和配置化 retention。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiIdempotencyReplayService.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/repository/OpenAiIdempotencyRecordRepository.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiIdempotencyReplayServiceTests.java`
- `docs/public-api-compatibility.md`
- `tasks/index.md`

## 依赖

- `TASK-20260515-009` 已提供幂等记录 entity、repository 与 replay service。
- 应用已启用 Spring scheduling。

## 风险

- retention 太短会让客户端晚于窗口重试时重新执行请求；默认 24 小时先作为保守基线。
- scheduled cleanup 需要避免影响测试确定性；测试只直接调用 service 方法，不依赖调度线程。
- 数据库时间和应用时间存在偏差时，cutoff 会以应用时钟为准。

## 验收标准

- 默认 retention 为 24 小时。
- `purgeExpiredRecords(now)` 会按 `now - retention` 调用 repository 删除。
- scheduled cleanup 使用同一个 service 清理逻辑。
- 幂等 replay/remember 既有测试继续通过。
- 文档说明 retention 与配置项。

## 测试边界

- `OpenAiIdempotencyReplayServiceTests` 覆盖清理 cutoff 与删除计数。
- 不跑真实 provider smoke。

## 实现结果

- `OpenAiIdempotencyRecordRepository` 增加 `deleteByCreatedAtBefore(Instant cutoff)`。
- `OpenAiIdempotencyReplayService`：
  - 增加 `gateway.openai.idempotency.retention` 配置，默认 `PT24H`；
  - retention 配置为空、零值或负值时回退到默认 24 小时；
  - 增加 `purgeExpiredRecords(Instant now)`，按 `now - retention` 删除过期记录；
  - 增加 scheduled cleanup，默认 `gateway.openai.idempotency.cleanup-fixed-delay=PT1H`；
  - 既有 replay/remember 语义保持不变。
- `docs/public-api-compatibility.md` 与 docs bundle 增加幂等记录 retention 说明。

## 验证结果

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiIdempotencyReplayServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiChatCompletionsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiResponsesControllerTests"`

## 遗留问题

- 本轮未提供 Admin/Ops 手动触发 API；service 方法已经可供后续接入。
- retention 使用应用时间计算 cutoff，后续如果引入多实例强一致清理，可改为数据库时间或统一 clock service。

## 关联文档

- [TASK-20260515-009](../done/TASK-20260515-009-openai-idempotency-key-response-replay.md)
- [TASK-20260514-030](../done/TASK-20260514-030-openai-cross-cutting-protocol-compatibility.md)
