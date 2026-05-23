# TASK-20260523-005-01 OpenAI-style Audio 与 Images 资源入口补齐

## 任务类型

子任务

## 背景

来源：`tasks/done/TASK-20260523-005-provider-audio-file-image-resource-coverage-parent.md`

当前 OpenAI-style 资源入口缺少 `POST /v1/audio/translations`、`POST /v1/images/edits`、`POST /v1/images/variations`。这些路径在 OpenAI 官方 API 中属于现役资源型接口，且现有 OpenAI-style passthrough executor 已按 `/v1/audio/`、`/v1/images/` 前缀支持直连，主要缺口在 ingress、语义识别、operation/feature 和能力矩阵。

## 目标

- 新增 audio translation multipart 入口。
- 新增 image edit multipart 入口。
- 新增 image variation multipart 入口。
- 新增对应 `TranslationOperation` 和 `InteropFeature`。
- 更新语义识别、默认 path/surface、能力矩阵和路由策略。
- 补 controller 与 semantics 测试。

## 非目标

- 不在本子任务内实现 Gemini/Vertex image edit/variation 原生互转。
- 不在本子任务内调整 file* 编排策略。
- 不做真实上游付费 smoke。

## 上游来源

- `docs/requirements/REQ-20260523-004-provider-audio-file-image-resource-coverage.md`
- `tasks/done/TASK-20260523-005-provider-audio-file-image-resource-coverage-parent.md`

## 输入

- 当前 `OpenAiAudioController`
- 当前 `OpenAiImagesController`
- 当前 `GatewayRequestFeatureService`
- 当前 `ExecutionSupportMatrixService`
- 当前 `SiteCapabilityTruthService`

## 输出

- 新增 OpenAI-style audio/image 资源入口代码。
- 新增/更新测试。
- 文档与任务回写。

## 影响范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/TranslationOperation.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/InteropFeature.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/GatewayRequestFeatureService.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/GatewayRequestSemantics.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiAudioController.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiImagesController.java`
- 对应后端测试文件

## 依赖

- `GatewayResourceExecutionService.executeMultipartJson`
- OpenAI-style audio/images passthrough executor
- 现有 distributed key authentication

## 风险

- multipart 字段列表需要既覆盖官方当前参数，又避免误处理文件数组等尚未建模复杂形态。
- capability 判定不能把 Gemini/Vertex 原生 executor 未支持的 edit/variation 误报为 native。

## 验收标准

- [x] `POST /v1/audio/translations` 可进入 `AUDIO_TRANSLATION` semantics。
- [x] `POST /v1/images/edits` 可进入 `IMAGE_EDIT` semantics。
- [x] `POST /v1/images/variations` 可进入 `IMAGE_VARIATION` semantics。
- [x] controller 测试证明 multipart 字段透传到 `GatewayResourceExecutionService`。
- [x] 能力矩阵测试证明 OpenAI-style 可支持，未实现原生互转的厂商不被误标。
- [x] 编译和定向测试通过。

## 测试边界

- `OpenAiAudioControllerTests`
- `OpenAiImagesControllerTests`
- `GatewayRequestFeatureServiceTests`
- `ExecutionSupportMatrixServiceTests`
- `SiteCapabilityTruthServiceTests`
- `compileTestJava` 或定向 Gradle test

## 关联文档

- `docs/requirements/REQ-20260523-004-provider-audio-file-image-resource-coverage.md`

## 关联任务

- `tasks/done/TASK-20260523-005-provider-audio-file-image-resource-coverage-parent.md`
- `tasks/done/TASK-20260523-005-02-provider-file-object-orchestration-coverage.md`
- `tasks/done/TASK-20260523-005-03-provider-native-media-adapter-parity.md`

## 实现结果

- `OpenAiAudioController` 新增 `/v1/audio/translations` multipart 入口。
- `OpenAiImagesController` 新增 `/v1/images/edits` 和 `/v1/images/variations` multipart 入口。
- `GatewayRequestFeatureService`、`GatewayRequestSemantics`、`CanonicalExecutionPlan`、`DefaultCanonicalResourceMapper` 已识别并归类新增资源操作。
- `ExecutionSupportMatrixService` 和 `SiteCapabilityTruthService` 将新增能力限定为 OpenAI-style 已实现面，避免 Gemini/Vertex 未实现原生互转时误报。
- `ProviderSiteAdminService` 已在 feature/surface view 中暴露新增能力。
- `PublicDocsBundleService` 与 `docs/openapi/public-openapi.json` 已声明新增公开路径。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiAudioControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiImagesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceMapperTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests"`
- `.\gradlew.bat compileTestJava`

## 遗留问题

- image edit 当前只建模单个 `image` 文件和可选 `mask`，OpenAI 官方多图数组形态后续可在原生互转或参数 parity 任务中扩展。
- 本子任务未覆盖真实上游付费 smoke。

## 当前状态

Done
