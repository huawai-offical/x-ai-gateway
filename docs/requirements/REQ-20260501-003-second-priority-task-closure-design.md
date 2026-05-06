# REQ-20260501-003 第二批最高优先级任务闭环设计

状态：Done  
日期：2026-05-01  
关联任务：

- [TASK-20260501-013 动态 Provider Catalog 与 Conformance Loader](../../tasks/done/TASK-20260501-013-dynamic-provider-catalog.md)
- [TASK-20260501-014 Async Media Provider Executors 与任务状态存储](../../tasks/done/TASK-20260501-014-async-media-executors.md)
- [TASK-20260501-015 路由策略配置 UI、Retry/Fallback 与熔断模型](../../tasks/done/TASK-20260501-015-routing-policy-config-ui.md)

## 背景

上一轮已完成 provider preset、非 Chat resource canonical 语义、路由候选 scoreBreakdown。为了继续沿同一条主线闭环，本轮选择三个直接后续任务：动态 Provider Catalog、Async Media 任务状态、路由策略配置模型。

## 目标

- Provider preset 从硬编码目录推进到 classpath catalog loader，并暴露版本、来源、deprecated 和 conformance metadata。
- Video / Music 资源从“能识别路径”推进到“能创建、查询、取消本地 async task 状态”。
- Route Guard 从纯阻断治理推进到可携带 retry / fallback / circuit breaker / rate limit 配置，便于前端策略页和 preview 读取。

## 本轮范围

### TASK-013 动态 Provider Catalog

- 新增 `provider-catalog.json` 作为本地 catalog 文件。
- 新增 catalog loader，启动时读取 classpath JSON，缺失或解析失败时退回内置 fallback。
- provider preset response 增加：
  - `catalogVersion`
  - `catalogSource`
  - `deprecated`
  - `conformanceChecks`
- 现有 preset API 与导入逻辑保持兼容。

### TASK-014 Async Media Provider Executors

- 扩展 `GatewayAsyncResourceType`：`VIDEO`、`MUSIC`。
- 在 `GatewayAsyncResourceService` 增加本地 media task executor：
  - create：生成 `video_` / `music_` gateway resource key。
  - get：读取本地 resource 状态。
  - cancel：将非终态任务改为 `cancelled` 并写入事件。
- 在 public API 增加：
  - `POST /api/v1/videos/generations`
  - `GET /api/v1/videos/{videoId}`
  - `POST /api/v1/videos/{videoId}/cancel`
  - `POST /api/v1/music/generations`
  - `GET /api/v1/music/{musicId}`
  - `POST /api/v1/music/{musicId}/cancel`
- `operations` 列表支持 `video`、`music` resourceType。

### TASK-015 路由策略配置

- 扩展 `RouteGuardPolicy` schema，增加后续策略配置字段：
  - `retryPolicy`
  - `fallbackPolicy`
  - `circuitBreakerPolicy`
  - `rateLimitPolicy`
- Admin API request/response 返回这些字段。
- 增加 routing policy summary API，供前端先读取策略分布和启用状态。

## 非目标

- 不在本轮接入真实第三方 Video / Music provider。
- 不在本轮完成完整前端策略配置页面。
- 不引入分布式调度器或后台 worker。

## 风险

- classpath catalog 是本地动态加载的第一步，不等于远程 marketplace。
- Media task 是 gateway-local executor，适合作为 contract 与状态闭环，不应误认为已完成真实 provider 执行。
- 路由策略字段先作为配置承载，不在本轮改变路由排序行为。

## 验收标准

- catalog loader 可读取本地 JSON，并在 preset response 中返回版本、来源和 conformance 信息。
- Video / Music task 可 create/get/cancel，并可被 operations 与 async admin 查询。
- Route Guard policy 可保存并返回 retry/fallback/circuit/rate limit 配置。
- 三个任务均有后端单元测试覆盖，完成后移动到 `tasks/done/` 并回写验证结果。

## 实现结果

- 完成 `provider-catalog.json`、`ProviderCatalogLoader`、`ProviderCatalogSnapshot`、`ProviderPresetDefinition`，preset API 返回 `catalogVersion`、`catalogSource`、`deprecated`、`conformanceChecks`。
- 完成 gateway-local Video / Music async task：`create/get/cancel`，新增 `/api/v1/videos/*` 与 `/api/v1/music/*` 入口，`operations` 支持 `video`、`music` 查询与取消。
- 完成 Route Guard 策略配置字段持久化：`retryPolicy`、`fallbackPolicy`、`circuitBreakerPolicy`、`rateLimitPolicy`，新增 `/admin/ops/policies/routing-summary` 摘要 API 与 Liquibase `0040` 变更。

## 测试/验证

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayPublicResourceServiceTests" --tests "com.prodigalgal.xaigateway.admin.application.GovernanceAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.GovernanceAdminControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.GatewayPublicResourceControllersTests"`
- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyEngineServiceTests"`

## 遗留问题

- Media task 当前是本地 contract 与生命周期闭环，尚未接入真实 Video / Music provider executor。
- Route policy 配置字段已持久化并可读写，本轮不改变路由排序、retry 执行、熔断运行时行为。
- Provider Catalog 当前支持 classpath 本地加载与 fallback，远程 catalog marketplace、签名校验、自动更新仍待后续任务。
- 后续已拆分到本地 backlog：`TASK-20260501-016`、`TASK-20260501-017`、`TASK-20260501-018`。
