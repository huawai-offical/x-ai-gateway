# TASK-20260522-009 分发 Key 协议簇授权迁移

## 任务类型

父任务

## 背景

来源：`docs/requirements/REQ-20260522-006-protocol-suite-authorization-migration.md`

用户指出创建密钥时“允许的协议”展示 `responses` 会造成误解，因为 `responses` 属于 OpenAI 入口 endpoint，而不是厂商协议簇。进一步确认后，本任务不删除授权维度，而是将 `allowedProtocols` 迁移为 `allowedProtocolSuites`，并且不保留旧字段兼容。

## 目标

- 后端、数据库、前端统一使用 `allowedProtocolSuites`。
- 路由和目录查询按候选站点映射协议簇后过滤。
- 前端创建 Key 和访问组时展示厂商协议簇选项。
- 存量数据通过 Liquibase 迁移到新列和新值。

## 非目标

- 不支持旧请求字段 `allowedProtocols`。
- 不修改站点/模型 capability 的 `supportedProtocols` 语义。
- 不做真实外部厂商调用。
- 不处理 Responses 与 Chat Completions 的协议转换实现。

## 上游来源

- `docs/requirements/REQ-20260522-006-protocol-suite-authorization-migration.md`
- 用户关于“不需要兼容旧字段 allowedProtocols，直接做迁移”的明确要求。

## 输入

- 后端 `DistributedKeyEntity`、`AccessGroupEntity`、相关 DTO 和 Service。
- 路由 `GatewayRouteSelectionService` 与目录 `ModelCatalogQueryService`。
- 前端 Key、访问组、门户 Key、Codex onboarding 页面。
- Liquibase 当前 baseline 与 `0002-model-policy` changelog。

## 输出

- 新协议簇映射工具。
- 字段、列名和 API DTO 迁移。
- 前端表单和展示迁移。
- Liquibase 直接迁移 changeset。
- 验证记录与任务归档。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/DistributedKeyEntity.java`
- `src/main/java/com/prodigalgal/xaigateway/infra/persistence/entity/AccessGroupEntity.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/*DistributedKey*`
- `src/main/java/com/prodigalgal/xaigateway/admin/api/*AccessGroup*`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/*Key*/*AccessGroup*`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/auth/*`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/routing/GatewayRouteSelectionService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/catalog/ModelCatalogQueryService.java`
- `src/main/resources/db/changelog/changes/db.changelog-0003-protocol-suite-authorization.yaml`
- `web/src/features/keys/*`
- `web/src/features/accounts/*`
- `web/src/features/portal/*`
- `web/src/features/user-domain/access-groups-page.tsx`

## 依赖

- 现有 `UpstreamSiteKind`
- 现有 `ProviderType`
- 现有 `StringListJsonConverter`
- 现有前端 API client 与表单组件

## 风险

- 直接迁移会破坏旧 API payload；这是本任务的明确取舍。
- 前端页面多，字段漏改会导致类型检查失败。
- 存量数据中的旧协议值只能做保守映射，不能还原真实厂商意图。

## 验收标准

- [ ] 代码中不再存在 `allowedProtocols` 业务字段。
- [ ] 数据库不再使用 `allowed_protocols_json` 列。
- [ ] 路由判断使用 `allowedProtocolSuites` 和候选 `siteKind`。
- [ ] 前端创建 Key 不再把 `responses` 当作允许协议选项。
- [ ] 后端 targeted tests 与编译通过。
- [ ] 文档和任务状态回写完成。

## 测试边界

- 后端单元测试覆盖 AccessGroup entitlement、Admin Service、Portal key 创建、模型目录或路由协议簇过滤。
- 前端至少执行类型检查或相关页面测试。
- 不执行真实 MiMo/DeepSeek 外部请求。

## 关联文档

- `docs/requirements/REQ-20260522-006-protocol-suite-authorization-migration.md`

## 关联任务

- `tasks/done/TASK-20260522-007-model-policy-layered-resolution-parent.md`
- `tasks/done/TASK-20260522-008-model-refresh-idempotency.md`

## 当前状态

In Progress
