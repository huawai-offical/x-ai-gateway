# TASK-20260523-006 资源入口事实源去重

## 任务类型

子任务

## 背景

来源：`docs/requirements/REQ-20260523-005-resource-surface-registry-dedup.md`

用户指出入口能力矩阵和特性解析重复。当前同一资源入口被手工维护在 `GatewayRequestFeatureService`、`GatewayRequestSemantics`、`CanonicalExecutionPlan`、`TranslationExecutionPlanCompiler` 和 `ProviderSiteAdminService` 中。新增一个 endpoint 需要改多处，容易产生展示与运行时不一致。

## 目标

- 新增统一资源入口 registry。
- 静态资源路径解析改为 registry 查表。
- 默认 surface/path/default model 改为 registry 派生。
- 厂商详情入口矩阵改为 registry 派生。
- 厂商详情页删除独立特性解析重复区块，把 feature 解析合并到入口能力矩阵。
- 保留 body-dependent endpoint 的专门解析逻辑。

## 非目标

- 不调整真实厂商能力判定。
- 不实现 file* 后续覆盖。
- 不实现原生媒体互转。
- 不做厂商详情整页重设计；只做重复信息收敛。

## 上游来源

- `docs/requirements/REQ-20260523-005-resource-surface-registry-dedup.md`
- 用户原话：“入口能力矩阵和特性解析是不是重复了？你为什么做这么多重复的表格和功能？”以及“开始清理”

## 输入

- `GatewayRequestFeatureService`
- `GatewayRequestSemantics`
- `CanonicalExecutionPlan`
- `TranslationExecutionPlanCompiler`
- `ProviderSiteAdminService`
- 上一轮新增 audio/image 资源接口

## 输出

- 统一 registry 代码。
- 被替换的重复清单。
- 定向测试与文档回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/canonical/CanonicalExecutionPlan.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/ProviderSiteAdminService.java`
- `web/src/features/provider-sites/provider-site-detail-page.tsx`
- `src/test/java/com/prodigalgal/xaigateway/`
- `docs/`
- `tasks/`

## 依赖

- 现有 `TranslationOperation` 和 `InteropFeature`。
- 现有 capability truth service。
- 现有 provider site response contract。

## 风险

- registry 覆盖漏项会影响 resource endpoint 路由。
- 入口矩阵展示顺序变化可能影响测试断言。
- 旧代码路径仍可能残留少量 OpenAPI schema 重复，本轮需记录边界。

## 测试边界

- `GatewayRequestFeatureServiceTests`
- `CanonicalResourceMapperTests`
- `ProviderSiteAdminServiceTests`
- `provider-site-detail-page.test.tsx`
- `PublicDocsBundleServiceTests`
- `PublicOpenApiSnapshotTests`
- OpenAI audio/images controller tests
- `compileTestJava`

## 关联文档

- `docs/requirements/REQ-20260523-005-resource-surface-registry-dedup.md`

## 关联任务

- `tasks/done/TASK-20260523-005-provider-audio-file-image-resource-coverage-parent.md`
- `tasks/done/TASK-20260523-005-01-openai-style-audio-image-resource-endpoints.md`

## 实现结果

- 新增 `ResourceSurfaceDefinition`。
- 新增 `ResourceSurfaceRegistry` 作为静态资源入口事实源。
- `GatewayRequestFeatureService`、`GatewayRequestSemantics`、`CanonicalExecutionPlan`、`TranslationExecutionPlanCompiler` 和 `ProviderSiteAdminService` 已改为从 registry 派生重复信息。
- 厂商详情页高级诊断删除独立“特性解析”区块，将 feature 解析状态合并到“入口能力矩阵”行内。
- 新增 `ResourceSurfaceRegistryTests` 防止后续新增入口绕过 registry。

## 验收标准

- [x] `GatewayRequestFeatureService` 静态资源入口解析从 registry 派生。
- [x] `GatewayRequestSemantics` 和 `CanonicalExecutionPlan` 默认 surface/path 从 registry 派生。
- [x] `TranslationExecutionPlanCompiler` 默认模型从 registry 派生。
- [x] `ProviderSiteAdminService` surfaces 从 registry 派生。
- [x] 厂商详情页不再展示独立“特性解析”重复区块。
- [x] 定向测试通过。
- [x] 文档和任务状态回写。

## 验证记录

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.ResourceSurfaceRegistryTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceMapperTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiAudioControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiImagesControllerTests"`。
- 通过：`.\gradlew.bat compileTestJava`。
- 通过：`bun run test -- provider-site-detail-page.test.tsx`。
- 通过：`bun run typecheck`。
- 浏览器轻量验证：`http://127.0.0.1:5174/console/provider-sites/1` 当前本机会话未登录，按预期重定向到登录页；页面非空、无框架错误覆盖、无 console warning/error。登录后的详情页 UI 由组件测试覆盖。

## 遗留问题

- public OpenAPI requestBody schema 的重复不在本轮范围。
- `TASK-20260523-005-02` 和 `TASK-20260523-005-03` 已分别完成 file* 编排和厂商原生 media 互转。

## 当前状态

Done
