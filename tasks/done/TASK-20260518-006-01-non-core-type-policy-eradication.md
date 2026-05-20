# TASK-20260518-006-01 类型、策略与 capability 残留清理

状态：Done
优先级：Critical
类型：子任务
父任务：[TASK-20260518-006](TASK-20260518-006-non-core-api-code-eradication.md)
上游来源：[REQ-20260518-006](../../docs/requirements/REQ-20260518-006-non-core-api-code-eradication.md)

## 背景

上一轮公开入口已删除，但内部 enum、capability、policy 和 execution matrix 仍保留 Batch/Tuning/Anthropic Message Batch 语义。用户明确要求不兼容历史数据，因此这些类型和策略分支必须从事实源移除。

## 目标

- 删除 `TranslationOperation`、`TranslationResourceType`、`InteropFeature`、`GatewayAsyncResourceType` 中的非核心 API 语义。
- 删除 capability snapshot、site policy、route policy、execution support matrix 中的 Batch/Tuning 分支。
- 保留 Vector Store File Batch，因为它属于 file_search ingestion 支撑。

## 非目标

- 不删除 Chat、Responses、Messages、GenerateContent、tools、Files、Uploads、Models、Embeddings、Vector Stores。
- 不删除平台自身 Admin Console。

## 输入

- `REQ-20260518-006`
- 残留符号搜索结果

## 输出

- 删除后的 enum、policy、capability 和 matrix 代码。
- 无 `BATCH_CREATE`、`TUNING_CREATE`、`ANTHROPIC_MESSAGE_BATCH` 等残留符号。

## 影响范围

- `gateway/core/interop`
- `gateway/core/site`
- `gateway/core/execution`
- `infra/persistence/entity/SiteCapabilitySnapshotEntity.java`
- `db.changelog-0013-site-profile-capability.yaml`

## 依赖

- `REQ-20260518-005` 已确定功能性服务 API 边界。

## 风险

- `batch` 命名可能误伤 Vector Store File Batch，需要按 resource type 区分。

## 验收标准

- 非核心 API enum 和 capability 字段已删除。
- 编译和 interop/policy 定向测试通过。

## 测试边界

- `ExecutionSupportMatrixServiceTests`
- `SiteCapabilityTruthServiceTests`
- `NonChatRoutePolicyServiceTests`
- `GatewayRequestFeatureServiceTests`

## 当前状态

- 2026-05-19：已完成并由父任务统一验证。
