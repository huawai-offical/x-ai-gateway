# TASK-20260523-005-03 厂商原生 Audio 与 Images 互转适配补齐

## 任务类型

子任务

## 背景

来源：`tasks/done/TASK-20260523-005-provider-audio-file-image-resource-coverage-parent.md`

OpenAI-style audio/images 入口可以通过直连覆盖 OpenAI 与兼容厂商，但 Gemini、Vertex、Anthropic 等厂商的原生接口并不总是与 OpenAI-style 一一对应。用户明确接受“互转或直连”，因此需要逐厂商确认 audio transcription、audio speech、audio translation、image generation、image edit、image variation 的可映射能力。

## 目标

- 为 Gemini/Vertex audio/image 原生能力建立互转映射表。
- 为 Anthropic audio/image 不具备等价资源接口的场景给出明确 blocked reason。
- 为 OpenAI-compatible 厂商保留 OpenAI-style 直连路径，并记录真实失败。
- 更新 executor、mapper、capability matrix 和 conformance fixture。

## 非目标

- 不把图像理解伪装成图像编辑。
- 不把普通文本回答伪装成 audio translation。
- 不做不可控的多步模型链路，除非有明确验收标准。

## 上游来源

- `docs/requirements/REQ-20260523-004-provider-audio-file-image-resource-coverage.md`
- `tasks/done/TASK-20260523-005-provider-audio-file-image-resource-coverage-parent.md`

## 输入

- `GeminiAudioGatewayResourceExecutor`
- `GeminiImagesGatewayResourceExecutor`
- `GoogleNativeNonChatCanonicalRenderer`
- `ExecutionBackendPolicyService`
- 厂商官方文档

## 输出

- 厂商原生 media adapter 的能力映射和实现。
- 不可映射能力的 blocked reason。
- 单元测试与后续真实 smoke 任务。

## 影响范围

- Google/Gemini/Vertex 原生资源执行器。
- Anthropic capability 判定。
- OpenAI-compatible passthrough 策略。
- conformance matrix。

## 依赖

- OpenAI-style audio/image 入口补齐。
- file* 编排能力对齐。
- 可用于真实 smoke 的厂商凭证。

## 风险

- 厂商模型能力变化快，需要基于官方文档和真实 smoke 迭代。
- image edit/variation 互转可能涉及 mask、透明背景、尺寸、格式等参数损耗。

## 验收标准

- [x] 每个目标厂商都有 audio/image 资源能力映射表。
- [x] 可直连能力走 passthrough 或 native executor。
- [x] 可互转能力有 mapper/executor 测试。
- [x] 不可支持能力在 capability matrix 中说明原因。

## 测试边界

- mapper/executor 单元测试。
- conformance matrix 测试。
- 真实 smoke 按凭证和成本单独排期。

## 关联文档

- `docs/requirements/REQ-20260523-004-provider-audio-file-image-resource-coverage.md`

## 关联任务

- `tasks/done/TASK-20260523-005-provider-audio-file-image-resource-coverage-parent.md`

## 执行计划

- 第一阶段：固化厂商媒体能力映射表，区分 OpenAI-style 直连、Gemini/Vertex 原生执行和不可等价能力。
- 第二阶段：收紧 runtime capability 与 executor 支持边界，避免 `audio_translation`、`image_edit`、`image_variation` 被误路由到不支持的原生执行器。
- 第三阶段：补充 conformance fixture、matrix 测试和 executor 单元测试，真实 smoke 另行排期。

## 厂商能力映射

| 厂商入口 | audio transcription | audio speech | audio translation | image generation | image edit | image variation |
| --- | --- | --- | --- | --- | --- | --- |
| OpenAI / OpenAI-compatible | OpenAI-style passthrough | OpenAI-style passthrough | OpenAI-style passthrough | OpenAI-style passthrough | OpenAI-style passthrough | OpenAI-style passthrough |
| Gemini / Vertex | Google GenAI native | Google GenAI native | Blocked：无等价稳定 `/v1/audio/translations` | Google GenAI native | Google GenAI native `editImage` | Blocked：无等价稳定 `/v1/images/variations` |
| Anthropic | Blocked：无稳定原生 audio API | Blocked：无稳定原生 audio API | Blocked：无稳定原生 audio API | Blocked：无稳定原生 image API | Blocked：无稳定原生 image API | Blocked：无稳定原生 image API |

## 实现结果

- `GeminiImagesGatewayResourceExecutor` 已支持 OpenAI-style `POST /v1/images/edits` 到 Google GenAI `editImage` 的原生执行映射，覆盖输入图、可选 mask、`n`、`output_format`、`output_compression` 与 OpenAI-compatible `b64_json` 响应。
- `ExecutionBackendPolicyService`、`ExecutionSupportMatrixService` 与 `SiteCapabilityTruthService` 已收紧媒体资源边界：Gemini/Vertex 的 `image_edit` 为 native，`audio_translation` 与 `image_variation` 明确 blocked；Anthropic audio/image 生成类继续明确 blocked；OpenAI-compatible 保持 passthrough。
- `RouteSelectionRequest`、`GatewayRouteSelectionService`、`TranslationExecutionPlanCompiler` 和管理端 preview request 已传递 `httpMethod`，避免 GET/DELETE/file lifecycle 资源预览被误按 POST 解析。
- conformance matrix 新增 OpenAI-compatible `audio_translation`、`image_edit`、`image_variation` 和 Gemini `image_edit` 证据行；site fixture 新增 Gemini `image_edit` native 与 `image_variation` blocked 场景。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.GeminiImagesGatewayResourceExecutorTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionGovernanceTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.RoutingPreviewControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.ExecutionPreviewControllerTests"`
- `.\gradlew.bat compileJava compileTestJava`

## 遗留问题

- 本任务不执行真实上游 smoke；需要真实凭证、成本预算和可回放脱敏 fixture 时，归入现有 smoke 任务体系继续排期。

## 当前状态

Done
