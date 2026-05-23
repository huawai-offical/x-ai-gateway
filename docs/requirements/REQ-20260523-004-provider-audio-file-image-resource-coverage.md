# REQ-20260523-004 厂商 Audio、Files、Images 资源型接口覆盖

## 背景

用户要求各个厂商的 `audio` 接口、`file*` 接口、`image*` 接口都需要实现，不管是协议互转还是厂商直连。现有实现已经有部分资源型入口：

- OpenAI-style `POST /v1/audio/transcriptions`、`POST /v1/audio/speech`、`POST /v1/images/generations`。
- OpenAI-style `files`、`uploads` 本地编排入口。
- Gemini/Vertex/Anthropic 的文件对象绑定已有局部实现。

但当前资源族仍存在明显缺口：OpenAI-style 缺少 `POST /v1/audio/translations`、`POST /v1/images/edits`、`POST /v1/images/variations`；部分厂商文件对象能力在 capability matrix 中仍被保守标为未泛化；Gemini/Vertex/Anthropic 等原生媒体能力和 OpenAI-style 资源接口之间还没有完整互转口径。

本需求覆盖旧文档中“Audio translations、Images edits/variations 不作为现役支持面”的历史收口。自本需求起，这些接口进入现役资源型接口覆盖范围，按可验证切片逐步补齐。

## 目标

- 补齐 OpenAI-style 资源入口缺口：
  - `POST /v1/audio/translations`
  - `POST /v1/images/edits`
  - `POST /v1/images/variations`
- 为 `audio`、`file*`、`image*` 建立跨厂商能力判定口径：
  - 上游原生支持时优先直连或原生执行。
  - 上游协议不一致但语义可映射时进入互转适配。
  - 上游不具备等价能力时给出明确 blocked reason，而不是静默缺失。
- 收敛 capability matrix、厂商目录、协议入口展示与运行时选择，使用户看到的能力与实际路由一致。
- 保持文件对象链路可追踪：本地文件记录、上游绑定、stored lineage、删除和内容读取边界必须清晰。

## 范围

- 后端资源语义识别：
  - `GatewayRequestFeatureService`
  - `GatewayRequestSemantics`
  - `TranslationOperation`
  - `InteropFeature`
- 后端执行和能力判定：
  - `ExecutionSupportMatrixService`
  - `ExecutionBackendPolicyService`
  - `SiteCapabilityTruthService`
  - `UpstreamSitePolicyService`
  - 资源执行器与文件编排服务相关测试
- OpenAI-style public ingress：
  - `OpenAiAudioController`
  - `OpenAiImagesController`
  - 相关 controller tests
- 文档与任务：
  - `docs/index.md`
  - `tasks/index.md`
  - 相关功能性 API 支撑文档按实现进度回写

## 非目标

- 本轮不承诺一次性完成所有厂商所有专有端点的真实远端 smoke。
- 本轮不恢复 Fine-tuning、Batches、Evals 等非当前用户点名资源族。
- 本轮不把不可等价转换的上游能力伪装为已支持。
- 本轮不改变对话主线 API 的路由策略，除非文件对象绑定或多模态输入必须同步。

## 外部事实源

- OpenAI API Reference：Audio translations、transcriptions、speech。
- OpenAI API Reference：Images generations、edits、variations。
- OpenAI API Reference：Files、Uploads。
- Anthropic API Reference：Files API 当前为 beta，需要 `anthropic-beta: files-api-2025-04-14`。
- Gemini API Docs：Files API 支持上传、获取、列表、删除，并可把文件用于 `generateContent`。
- Vertex AI Docs：图像生成能力与 Google 原生图像模型相关。

## 方案

### 第一切片：OpenAI-style 直连接口补齐

- 新增 `AUDIO_TRANSLATION`、`IMAGE_EDIT`、`IMAGE_VARIATION` 操作和 feature。
- 新增 OpenAI-style multipart controller：
  - audio translation 使用 `file`、`model`、`prompt`、`response_format`、`temperature`。
  - image edit 使用 `image`、可选 `mask` 和图像生成相关表单字段。
  - image variation 使用 `image` 和 variation 相关表单字段。
- 让现有 OpenAI-style audio/images passthrough executor 直接处理新增路径。
- 保持 Gemini/Vertex 原生执行器暂只承接已经实现的 transcription、speech、generation，避免把 edit/variation/translation 路由到未实现 executor。

### 第二切片：file* 编排覆盖与 capability 对齐

- 复核 `GatewayFileService` 已有 OpenAI、Anthropic、Gemini/Vertex 文件对象绑定。
- 调整 OpenAI-compatible generic 的 `FILE_OBJECT`、`UPLOAD_CREATE` 支持判定，只在 capability snapshot 声明 `supports_files=true` / `supports_uploads=true` 且 provider boundary 未排除时开放 gateway orchestration。
- 让厂商目录、预设导入、能力矩阵展示同一套文件对象事实源。

### 第三切片：厂商原生媒体互转适配

- Gemini/Vertex：评估 audio translation、image edit、image variation 是否能通过 `generateContent`、文件输入、图像生成模型或 Imagen/Nano Banana 能力等价映射。
- Anthropic：文件对象按 beta Files API 映射；audio/image 生成类如无等价 API，必须明确 blocked reason。
- OpenAI-compatible 厂商：按 OpenAI-style 入口直连，失败时保留上游错误和 traceId。

## 风险

- 不同厂商的 `image edit`、`image variation` 语义并不完全等价，强行互转可能改变用户预期。
- `file*` 能力有的厂商是长期文件对象，有的厂商是短生命周期 media upload，需要保留 lineage 和过期状态。
- OpenAI-compatible 厂商虽然暴露 OpenAI-style Base URL，但不一定实现全部 OpenAI resource endpoints，需要运行时失败可观测。
- Multipart 参数如果过度裁剪，会破坏新模型参数透传；如果完全放开，需要避免无意接受不可处理的字段。

## 验收标准

- OpenAI-style `audio translations`、`images edits`、`images variations` 有公开 ingress、语义识别、operation/feature、能力矩阵和 controller 测试。
- 新增接口能走现有 OpenAI-style passthrough，不再返回 unknown semantics。
- Gemini/Vertex 原生执行器不会误接未实现的 translation/variation；`image_edit` 已通过 Google GenAI `editImage` 原生执行映射。
- `file*` 任务已完成能力矩阵、conformance 与运行时编排对齐。
- 文档、任务、测试记录回写完成。

## 实现结果

- 第一切片已完成 OpenAI-style 直连资源入口：
  - `POST /v1/audio/translations`
  - `POST /v1/images/edits`
  - `POST /v1/images/variations`
- 第二切片已完成 file* 编排覆盖与 capability 对齐：
  - `POST /v1/files`、`GET /v1/files`、`GET /v1/files/{fileId}`、`GET /v1/files/{fileId}/content`、`DELETE /v1/files/{fileId}`
  - `POST /v1/uploads`、`POST /v1/uploads/{uploadId}/parts`、`POST /v1/uploads/{uploadId}/complete`、`POST /v1/uploads/{uploadId}/cancel`、`GET /v1/uploads/{uploadId}`
- 新增 `AUDIO_TRANSLATION`、`IMAGE_EDIT`、`IMAGE_VARIATION` operation 与 feature。
- 更新 route semantics、默认 surface/path、canonical resource mapper、public OpenAPI、厂商详情 feature/surface view。
- 能力判定按“已实现才 native”收口：
  - OpenAI-style 站点支持新增 audio/image 资源入口。
  - Gemini/Vertex 支持 audio transcription、audio speech、image generation 与 image edit 的原生执行；audio translation、image variation 明确 blocked。
  - Anthropic 对 audio/image 生成类继续返回明确 blocked reason。
- file* / uploads 已按 capability snapshot 与 provider boundary 驱动：OpenAI-compatible 可用站点进入 gateway orchestration，xAI、Perplexity、Cohere、Jina、Dify 等明确排除对象生命周期的站点保持 blocked reason。
- 厂商原生媒体互转第三切片已完成：
  - `GeminiImagesGatewayResourceExecutor` 支持 `POST /v1/images/edits` 到 Google GenAI `editImage` 的映射，覆盖输入图、可选 mask、`n`、`output_format`、`output_compression` 和 `b64_json` 响应。
  - OpenAI-compatible 的 audio/image 新资源保持 passthrough。
  - Anthropic audio/image 生成类不伪装互转，统一 blocked reason。
- 管理端 routing/execution/model policy preview 已支持可选 `httpMethod`，与资源型接口的真实方法保持一致。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiAudioControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiImagesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceMapperTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests"`
- `.\gradlew.bat compileTestJava`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatRoutePolicyServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ResourceSurfaceRegistryTests" --tests "com.prodigalgal.xaigateway.admin.application.ProviderSiteAdminServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.ProviderSiteAdminControllerTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiFilesControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.openai.OpenAiUploadsControllerTests" --tests "com.prodigalgal.xaigateway.protocol.ingress.google.GeminiFilesControllerTests" --tests "com.prodigalgal.xaigateway.gateway.core.file.GatewayFileServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.file.GatewayFileServiceAnthropicTests" --tests "com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceServiceTests"`
- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.GeminiImagesGatewayResourceExecutorTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.routing.GatewayRouteSelectionGovernanceTests" --tests "com.prodigalgal.xaigateway.admin.application.GatewayChatExecutionServiceTests" --tests "com.prodigalgal.xaigateway.admin.api.RoutingPreviewControllerTests" --tests "com.prodigalgal.xaigateway.admin.api.ExecutionPreviewControllerTests"`
- `.\gradlew.bat compileJava compileTestJava`

## 遗留问题

- 本轮未执行真实上游付费 smoke；后续需要基于真实凭证、成本预算和 record/replay fixture 进入 smoke 任务体系。

## 当前状态

Done
