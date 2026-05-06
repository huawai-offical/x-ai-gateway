# TASK-20260501-002 非 Chat 资源族扩展：Rerank / Video / Music / Task async lifecycle

状态：Done  
优先级：High  
来源：Linear X-283（已迁移到本地）  
关联报告：[REP-20260501](../../docs/reports/REP-20260501-open-source-gap-analysis.md)  
关联设计：[REQ-20260501-002](../../docs/requirements/REQ-20260501-002-priority-task-closure-design.md)

## 背景

`new-api` 支持 Rerank、Midjourney、Suno、Kling、Jimeng、Vidu、DoubaoVideo、Sora、Replicate 等非 Chat / 任务型资源；当前 `x-ai-gateway` 的 `TranslationResourceType` 尚未包含 `rerank`、`video`、`music`、`task`、`web_search` 等资源族。

## 本轮目标

扩展 canonical resource 抽象，使 Rerank、视频/音乐生成和长任务 lifecycle 可以进入统一语义识别、路由模式和能力判断。

## 本轮完成范围

- 新增资源类型：`RERANK`、`VIDEO`、`MUSIC`、`TASK`、`WEB_SEARCH`。
- 新增操作类型：rerank create、video create/get/cancel、music create/get/cancel、task get/cancel、web search create。
- 新增 interop feature：`RERANK`、`VIDEO_GENERATION`、`MUSIC_GENERATION`、`ASYNC_TASK`、`WEB_SEARCH`。
- `GatewayRequestFeatureService` 支持新资源路径识别、normalize path 与 path params。
- `GatewayRequestSemantics`、`CanonicalExecutionPlan`、`DefaultCanonicalResourceMapper`、`ExecutionBackendPolicyService`、`SiteCapabilityTruthService` 已补齐新资源的默认语义。

## 非目标

- 不承诺一次性覆盖所有图片/视频/音乐平台。
- 不引入未治理的透明代理。
- 不在本轮实现真实 Video / Music provider executor。

## 验收结果

- 新资源路径可被 `GatewayRequestFeatureService` 正确识别。
- async task create/get/cancel 能区分 `CATALOG_SELECTION` 与 `STORED_LINEAGE`。
- 兼容矩阵和 capability truth 对新 feature 保持保守，不会误报未实现 provider 可执行。

## 测试/验证

已通过：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteRegistryServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests"
```

关键覆盖：

- `shouldDescribeExtendedNonChatResourceSemantics`
- `shouldNormalizeExtendedResourcePathsAndExtractTaskParams`

## 遗留问题

- Video / Music / Task 真实执行器、状态表、lineage 存储、usage 归一和前端资源详情页仍需后续任务承接。
