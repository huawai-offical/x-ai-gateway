# REQ-20260501-002 最高优先级三个任务闭环设计

状态：Done  
日期：2026-05-01  
关联任务：

- [TASK-20260501-001 Provider Registry 2.0](../../tasks/done/TASK-20260501-001-provider-registry-2.md)
- [TASK-20260501-002 非 Chat 资源族扩展](../../tasks/done/TASK-20260501-002-non-chat-resources.md)
- [TASK-20260501-008 路由策略 2.0](../../tasks/done/TASK-20260501-008-routing-policy-2.md)

## 背景

对标 `new-api`、`Sub2API`、`CC Switch` 后，当前增强任务中 High 级任务较多。为了避免同时铺开过多长期工程，本轮选择对后续能力最有杠杆价值的三个任务优先闭环：供应商目录、非 Chat 资源抽象、路由策略解释性。

这三个方向分别对应：

- 上游供给侧：Provider preset 与站点 metadata。
- 资源抽象侧：Rerank、Video、Music、Task、Web Search 的 canonical 语义。
- 调度治理侧：路由候选为什么可用、为什么被过滤、分数如何得出。

## 目标

- 先落地本地设计、任务状态、验收记录，符合本仓库本地优先流程。
- 为 `Provider Registry 2.0` 提供可查询、可导入、幂等的 provider preset API。
- 为非 Chat 资源补齐 canonical enum、路径识别、path params 与基础兼容矩阵入口。
- 为路由策略补齐评分分解，让 route preview / API 调试能解释权重、优先级、亲和、成本惩罚与最终分数。

## 本轮范围

### Provider Registry 2.0

- 新增 provider preset response 与 import request。
- 管理端后端开放：
  - `GET /admin/provider-sites/presets`
  - `GET /admin/provider-sites/presets/{code}`
  - `POST /admin/provider-sites/presets/{code}/import`
- 内置至少 5 个代表性预设：OpenAI、Azure OpenAI、DeepSeek、OpenRouter、Anthropic、Gemini。
- 导入采用 `preset:{code}` profileCode，已存在则返回现有站点，不覆盖用户配置。
- 导入后刷新站点能力快照，使能力矩阵可立即显示基础 metadata。

### 非 Chat 资源族扩展

- 新增资源类型：`RERANK`、`VIDEO`、`MUSIC`、`TASK`、`WEB_SEARCH`。
- 新增操作类型：rerank、video create/get/cancel、music create/get/cancel、task get/cancel、web search。
- 新增能力特性：`RERANK`、`VIDEO_GENERATION`、`MUSIC_GENERATION`、`ASYNC_TASK`、`WEB_SEARCH`。
- `GatewayRequestFeatureService` 支持 OpenAI-style 路径归一、语义识别和路径参数提取。
- `CanonicalRenderCapabilitySupport` 对 OpenAI-style `/v1/*` 新资源先开放 render capability，真正 provider 能否执行仍由 capability truth 控制。

### 路由策略 2.0

- 不改变现有候选排序行为和 response record 结构。
- 扩展 `scoreBreakdown`，补充：
  - `priority_score`
  - `weight_score`
  - `affinity_bonus`
  - `cost_penalty`
  - `weighted_hash_jitter`
  - `total_score`
  - `retry_candidate`
  - `fallback_order`
- 对被过滤候选保留 `exclusionReasons`，并让 scoreBreakdown 同时暴露当前健康状态、选择来源和成本拒绝信息。

## 非目标

- 不一次性实现所有参考项目中的 provider adapter。
- 不在本轮完成 Video / Music provider 真实执行器。
- 不在本轮重构路由策略数据库模型或前端完整策略配置页。
- 不使用线上 Notion 或 Linear。

## 设计细节

### Provider preset 幂等导入

Provider preset 是静态目录，不新增数据库表。导入时生成 `UpstreamSiteProfileEntity`：

- `profileCode`：`preset:{code}`
- `profileSource`：`MANUAL`
- `active`：默认 true，可由 request 覆盖。
- provider family、auth、path、model addressing、error schema 来自 `UpstreamSitePolicyService.SitePolicy`。
- `description` 同时包含 preset 说明与来源，便于后续迁移到动态 registry。

如果 `profileCode` 已存在，直接返回现有站点响应，避免覆盖用户修改。

### 非 Chat 资源语义

本轮仅完成 gateway canonical 识别层，不假装 provider 已经可执行。请求语义按如下规则：

| 路径 | 资源 | 操作 | routeSelectionMode |
| --- | --- | --- | --- |
| `POST /v1/rerank` | RERANK | RERANK_CREATE | CATALOG_SELECTION |
| `POST /v1/videos/generations` | VIDEO | VIDEO_GENERATION_CREATE | CATALOG_SELECTION |
| `GET /v1/videos/{taskId}` | VIDEO | VIDEO_GENERATION_GET | STORED_LINEAGE |
| `POST /v1/videos/{taskId}/cancel` | VIDEO | VIDEO_GENERATION_CANCEL | STORED_LINEAGE |
| `POST /v1/music/generations` | MUSIC | MUSIC_GENERATION_CREATE | CATALOG_SELECTION |
| `GET /v1/music/{taskId}` | MUSIC | MUSIC_GENERATION_GET | STORED_LINEAGE |
| `POST /v1/music/{taskId}/cancel` | MUSIC | MUSIC_GENERATION_CANCEL | STORED_LINEAGE |
| `GET /v1/tasks/{taskId}` | TASK | TASK_GET | STORED_LINEAGE |
| `POST /v1/tasks/{taskId}/cancel` | TASK | TASK_CANCEL | STORED_LINEAGE |
| `POST /v1/web_search` | WEB_SEARCH | WEB_SEARCH_CREATE | CATALOG_SELECTION |

### Capability truth

新增 feature 默认保持保守：

- OpenAI-style render 层允许生成 canonical request。
- `ExecutionSupportMatrixService` 与 `SiteCapabilityTruthService` 只对明确支持的类型返回 native。
- 未声明 snapshot 或 provider 不支持时，仍被 capability truth 拦截为 unsupported。

### 路由解释性

路由选择不引入新字段，继续使用 `RouteCandidateEvaluation.scoreBreakdown()`。新增分数字段由同一个计算函数生成，保证排序分数和展示分数一致，避免 preview 与真实调度出现漂移。

## 风险

- 静态 provider preset 会变成维护负担；后续应迁移到可加载 catalog。
- 新资源路径进入 canonical 层后，如果调用方误以为已完成真实执行，会产生预期偏差；因此任务和文档需明确本轮只是语义与路由骨架。
- scoreBreakdown 文本是 API 输出的一部分，新增字段应保持向后兼容，不删除旧字段。

## 验收标准

- 三个任务文件已进入 `tasks/in-progress/` 并链接本设计文档。
- provider preset API 可列出、查看、导入，并具备幂等测试。
- 非 Chat 新路径语义识别、路径归一和路径参数提取有单元测试。
- 路由候选 scoreBreakdown 包含新增解释字段，且现有路由选择行为不回退。
- 测试结果回写到任务文件和本文档。

## 实现结果

- `Provider Registry 2.0`：新增 provider preset response/import request、静态 preset catalog、Admin API 列表/详情/导入接口，并实现 `preset:{code}` 幂等导入与能力快照刷新。
- `非 Chat 资源族扩展`：新增 Rerank、Video、Music、Task、Web Search 的 resource type、operation、interop feature、请求语义识别、路径归一和 path params 提取。
- `路由策略 2.0`：在不改变 `RouteCandidateEvaluation` 结构与排序行为的前提下，扩展 `scoreBreakdown`，展示 priority、weight、affinity、cost penalty、weighted hash jitter、total score、retry candidate 与 fallback order。

## 测试/验证

已通过定向后端测试：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests"
```

验证结果：BUILD SUCCESSFUL。

## 遗留问题

- Provider preset 仍是静态 catalog，后续应支持动态加载、版本化和 conformance 检查。
- Video / Music / Task 目前完成 canonical 语义层，真实 provider executor 与任务状态存储需继续拆分。
- 路由策略已增强解释性，但 retry/fallback/circuit breaker 的数据库化策略配置和前端配置页仍需后续任务承接。
