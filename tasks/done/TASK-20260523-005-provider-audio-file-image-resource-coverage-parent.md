# TASK-20260523-005 厂商 Audio、Files、Images 资源型接口覆盖

## 任务类型

父任务

## 背景

来源：`docs/requirements/REQ-20260523-004-provider-audio-file-image-resource-coverage.md`

用户要求各个厂商的 `audio`、`file*`、`image*` 接口都要实现，不管是协议互转还是厂商直连。当前项目已有部分 OpenAI-style 入口和文件对象编排，但 translations、image edits、image variations、跨厂商 file* 能力展示与原生互转口径仍不完整。

## 目标

- 统一资源型接口覆盖口径，覆盖 `audio`、`file*`、`image*`。
- 先补齐 OpenAI-style 直连缺口，使新增路径不再落到 unknown semantics。
- 拆出 file* 编排能力对齐和厂商原生媒体互转适配两个后续可执行子任务。
- 确保 capability matrix、厂商目录展示、运行时路由和测试边界一致。

## 非目标

- 不一次性承诺所有厂商真实远端 smoke。
- 不恢复用户未点名的非主线 API 族。
- 不伪造不可等价的厂商能力。

## 上游来源

- `docs/requirements/REQ-20260523-004-provider-audio-file-image-resource-coverage.md`
- 用户原话：“各个厂商的audio接口、file*接口、image*接口需要实现，不管是互转还是直连”

## 输入

- 当前 OpenAI-style audio/images/files/uploads controller。
- 当前 `GatewayFileService`、provider capability matrix 与执行器。
- OpenAI、Anthropic、Gemini、Vertex 官方资源型接口文档。

## 输出

- 本轮实现切片：OpenAI-style audio/image 缺口补齐。
- 后续切片：file* 编排覆盖、厂商原生媒体互转适配。
- 测试记录、文档回写和任务状态更新。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/site/`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/`
- `src/test/java/com/prodigalgal/xaigateway/`
- `docs/`
- `tasks/`

## 依赖

- 现有 OpenAI-style passthrough executor。
- 现有 multipart resource execution service。
- 现有 `GatewayFileService` 上游文件绑定能力。

## 风险

- 新增 operation/feature 会触发 switch exhaustiveness，需要同步更新语义、映射和测试。
- Google 原生 media executor 当前只支持已有子集，不能因为新增 feature 导致错误路由。
- OpenAI-compatible 站点的资源接口覆盖程度不一致，后续需要真实 smoke 或 capability override。

## 验收标准

- [x] 父任务拆分为可独立验证的子任务。
- [x] 第一子任务完成 OpenAI-style `audio translations`、`image edits`、`image variations`。
- [x] file* 和原生互转后续子任务进入 backlog 或 in-progress，并有清晰验收边界。
- [x] 第一切片文档与任务状态跟随实现回写。
- [x] file* 编排覆盖与能力矩阵对齐完成。
- [x] 厂商原生 Audio 与 Images 互转适配完成。

## 测试边界

- 第一切片：后端单元测试和编译。
- 后续 file*：文件创建、列表、获取、内容读取、删除、uploads 生命周期和上游 binding 测试。
- 后续原生互转：按厂商分组的 mapper/executor 单元测试，真实 smoke 单独排期。

## 关联文档

- `docs/requirements/REQ-20260523-004-provider-audio-file-image-resource-coverage.md`

## 关联任务

- `tasks/done/TASK-20260523-005-01-openai-style-audio-image-resource-endpoints.md`
- `tasks/done/TASK-20260523-005-02-provider-file-object-orchestration-coverage.md`
- `tasks/done/TASK-20260523-005-03-provider-native-media-adapter-parity.md`

## 实现结果

- 已归档第一子任务：OpenAI-style audio translations、image edits、image variations 入口补齐。
- 已归档第二子任务：file* / uploads 编排覆盖与能力矩阵对齐完成，OpenAI-compatible file/uploads 不再作为 accepted exception，而是由 capability snapshot 和 provider boundary 驱动。
- 已归档第三子任务：厂商原生媒体互转适配完成，Gemini/Vertex `image_edit` 进入 native executor，`audio_translation` 与 `image_variation` 明确 blocked；Anthropic audio/image 生成类明确 blocked；OpenAI-compatible 保持 OpenAI-style passthrough。
- 管理端 routing/execution/model policy preview 已透传 `httpMethod`，避免 file lifecycle 等 GET/DELETE 资源面在预览中按 POST 误判。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiAudioControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiImagesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceMapperTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests"`
- `.\gradlew.bat compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatRoutePolicyServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ResourceSurfaceRegistryTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiFilesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiUploadsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GeminiFilesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.file.GatewayFileServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.file.GatewayFileServiceAnthropicTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.GeminiImagesGatewayResourceExecutorTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionGovernanceTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.RoutingPreviewControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.ExecutionPreviewControllerTests"`
- `.\gradlew.bat compileJava compileTestJava`

## 剩余子任务

- 无。真实上游 smoke 另按凭证、成本和回放夹具进入 smoke 任务体系。

## 当前状态

Done
