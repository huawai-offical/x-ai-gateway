# REQ-20260523-005 资源入口事实源去重

## 背景

用户指出“入口能力矩阵”和“特性解析”存在重复：同一个资源入口需要同时维护运行时路径解析、厂商详情入口矩阵、默认 path/surface、默认模型和部分公开展示。上一轮补齐 `audio_translation`、`image_edit`、`image_variation` 时，为了保证行为闭环，按现有结构同步修改了多处手工清单，也暴露出事实源分散的问题。

## 目标

- 建立统一的资源入口事实源，集中描述：
  - HTTP method
  - normalized path
  - surface
  - resource type
  - operation
  - required features
  - route selection mode
  - default model
  - 是否展示到厂商入口能力矩阵
- `GatewayRequestFeatureService` 对静态资源入口从统一事实源解析，不再维护长串重复 `if`。
- `GatewayRequestSemantics` 和 `CanonicalExecutionPlan` 的默认 surface/path 从统一事实源派生。
- `TranslationExecutionPlanCompiler` 的非对话默认模型从统一事实源派生。
- `ProviderSiteAdminService` 的入口能力矩阵从统一事实源派生，feature 总览从 `InteropFeature.values()` 派生。
- 厂商详情页移除“入口能力矩阵”和“特性解析”双表重复，把特性解析并入入口行内展示。

## 范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/canonical/CanonicalExecutionPlan.java`
- `src/main/java/com/prodigalgal/xaigateway/admin/application/ProviderSiteAdminService.java`
- `web/src/features/provider-sites/provider-site-detail-page.tsx`
- 相关单元测试
- `docs/index.md`
- `tasks/index.md`

## 非目标

- 不改变各厂商真实支持能力。
- 不实现 file* 后续编排覆盖。
- 不实现 Gemini/Vertex/Anthropic audio/image 原生互转。
- 不重做前端厂商管理页面整体布局；本轮只移除特性解析重复表格。
- 不把 public OpenAPI 每个 endpoint 的 requestBody schema 强行抽象化，本轮只收敛后端运行态和入口矩阵事实源。

## 方案

- 新增 `ResourceSurfaceDefinition` record。
- 新增 `ResourceSurfaceRegistry`，作为资源入口集中事实源。
- 将 OpenAI-style、Google native files/embeddings、异步资源 follow-up 等静态入口注册到 registry。
- body-dependent 的 chat/responses/messages/generateContent 仍保留专门解析逻辑，只在静态资源入口回退到 registry。
- 厂商详情 `surfaces` 只展示 registry 中标记为 provider surface 的入口，避免跟运行时解析维护两份手工表。
- 前端高级诊断页保留单一入口能力矩阵，把每个入口所需 feature 和解析状态合并到同一行，删除独立“特性解析”区块。

## 风险

- registry 初始覆盖不完整会造成已有路径变成 unknown。
- 旧测试如果只验证局部路径，可能漏掉 surface 派生变化，需要保留 semantics 和 provider site tests。
- public OpenAPI schema 暂未完全抽象，仍需后续单独治理。

## 验收标准

- 静态资源入口语义解析来自 `ResourceSurfaceRegistry`。
- 默认 surface/path/default model 不再在多个类中重复手写同一张表。
- 厂商详情入口矩阵从 registry 派生。
- 厂商详情不再同时展示独立“入口能力矩阵”和“特性解析”两张重复表格。
- 新增接口和既有资源入口定向测试通过。
- 文档与任务状态回写。

## 当前状态

Done

## 实现结果

- 新增 `ResourceSurfaceDefinition` 和 `ResourceSurfaceRegistry`，集中维护静态资源入口的 method、normalized path、surface、protocol、resource type、operation、required features、route selection mode、default model 和 provider surface 展示标记。
- `GatewayRequestFeatureService` 已改为从 registry 解析静态资源入口；`chat.completions`、`responses` 仍保留 body-dependent feature 收集，但基础 surface/path/operation 来自 registry。
- `GatewayRequestSemantics`、`CanonicalExecutionPlan` 和 `TranslationExecutionPlanCompiler` 已改为从 registry 派生默认 surface/path/route selection mode/default model。
- `ProviderSiteAdminService` 的 feature 总览、provider surfaces 和模型级基础 surfaces 已从 registry 派生，删除原先多处手工表。
- 厂商详情页高级诊断只保留“入口能力矩阵”一张表，删除独立“特性解析”区块，并将每个入口的 feature 解析状态合并到入口行内展示。

## 验证情况

- 通过：`.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.ResourceSurfaceRegistryTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceMapperTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleServiceTests" --tests "com.prodigalgal.xaigateway.docs.PublicOpenApiSnapshotTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiAudioControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiImagesControllerTests"`。
- 通过：`.\gradlew.bat compileTestJava`。
- 通过：`bun run test -- provider-site-detail-page.test.tsx`。
- 通过：`bun run typecheck`。
- 浏览器轻量验证：`http://127.0.0.1:5174/console/provider-sites/1` 在当前本机未登录控制台会话下按预期重定向到登录页，页面非空、无框架错误覆盖、无 console warning/error；登录后的详情页渲染由组件测试覆盖。

## 遗留边界

- public OpenAPI 每个 endpoint 的 requestBody schema 仍有少量结构化重复，本轮未抽象，保留为后续单独治理。
- file* 编排覆盖、厂商原生 audio/image 互转仍归属 `TASK-20260523-005-02` 和 `TASK-20260523-005-03`。
