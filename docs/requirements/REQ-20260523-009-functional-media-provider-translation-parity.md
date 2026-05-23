# REQ-20260523-009 功能性媒体 API 厂商互转补齐

状态：Done  
提出时间：2026-05-23  
关联任务：`tasks/done/TASK-20260523-010-functional-media-provider-translation-parity.md`

## 背景

项目范围已明确不追求 OpenAI、Anthropic、Gemini、Vertex 等官方 API 全量覆盖。`admin`、`tuning`、`evals`、provider batch/job/pipeline 等非功能性 API 不进入当前实现目标。

当前仍需要继续推进的是功能性 API：对话、streaming、tools/function calling、多模态输入输出，以及直接支撑这些能力的 audio/image/file/RAG 类资源接口。上一轮 `REQ-20260523-004` 已补齐 OpenAI-style 资源入口和部分 Gemini/Vertex 原生映射，但仍将 Anthropic audio/image、Gemini/Vertex `audio_translation`、Gemini/Vertex `image_variation` 标记为 blocked。

用户明确要求继续完善这些厂商的 API 实现和互转：

- Anthropic 的 audio/image。
- Google Gemini 的 audio translation 和 image variation。

## 目标

- 在“不做官方全量 API”的前提下，补齐功能性媒体 API 的可执行互转路径。
- 为 Anthropic audio/image 提供可验证的能力拆分：Messages 图片输入理解保持支持；没有等价上游返回契约的 audio 资源与图片生成/编辑/variation 保持明确 blocked，不做假适配。
- 为 Gemini/Vertex `audio_translation` 提供稳定的功能性互转路径。
- 为 Gemini/Vertex `image_variation` 提供稳定的功能性互转路径。
- 更新 capability matrix、conformance fixture、公开兼容文档和任务状态，避免展示层继续显示已过时 blocked 状态。

## 非目标

- 不实现 Anthropic message batches、provider admin、evals。
- 不实现 Gemini/Vertex tuning、batch prediction、pipeline/job/admin。
- 不实现不可控的多步工作流，除非返回契约、失败语义、成本边界和测试夹具可固定。
- 不把厂商没有真实音频或图像输出能力的路径伪装成原生 native；互转需要标明 emulated/translated 语义。

## 范围

- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/interop/`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/execution/`
- `src/test/java/com/prodigalgal/xaigateway/gateway/core/interop/`
- `src/test/resources/conformance/`
- `docs/functional-service-api-coverage-matrix.md`
- `docs/multimodal-supporting-parameters.md`
- `docs/public-api-compatibility.md`

## 初始方案

- Gemini/Vertex `audio_translation`：复用 Gemini audio transcription 的多模态输入能力，将指令从 transcription 切换为“翻译为英文/目标语言文本”，以 OpenAI-style translations JSON 结果返回；不宣称生成音频。
- Gemini/Vertex `image_variation`：复用 Gemini image generation/edit 能力，将输入图片作为参考图，生成同主题/同构图变化图，返回 OpenAI-compatible `b64_json`。
- Anthropic audio/image：
  - image input：继续通过 Claude Messages 图片输入理解路径支持，不映射为 `/v1/images/*` 输出资源。
  - audio transcription/translation/speech：Anthropic 当前无稳定 audio API 和 OpenAI-style audio 资源返回契约，保持 unsupported。
  - image generation/edit/variation：Anthropic 当前无稳定图片生成、编辑或 variation 资源 API，保持 unsupported；本任务不把文本描述伪装成图片。

## 风险

- Anthropic 的 Messages 图片输入能力与 `/v1/images/*` 输出资源不是同一件事，展示层必须拆开，避免把 image input 误解成 image generation/edit/variation。
- Gemini `audio_translation` 是“音频到目标语言文本”，不是“音频到音频翻译”。
- Gemini `image_variation` 是参考图驱动生成，可能与 OpenAI variation 的像素级语义不同，需要在 capability 与文档中注明。

## 验收标准

- Gemini/Vertex `audio_translation` 不再显示为 blocked，能进入执行器并返回 OpenAI-style translation JSON。
- Gemini/Vertex `image_variation` 不再显示为 blocked，能进入执行器并返回 OpenAI-style image response。
- Anthropic audio/image 能力按可互转与不可互转拆分展示：图片输入理解可用，audio 资源与图片生成/编辑/variation 明确 blocked。
- 不支持的能力必须保留明确 blocked reason，不能假装成功。
- 新增/更新测试覆盖执行器、能力矩阵和 conformance fixture。

## 验证方式

- 聚焦执行器测试。
- `ExecutionSupportMatrixServiceTests`
- `SiteCapabilityTruthServiceTests`
- `EndpointConformanceMatrixTests`
- `SiteConformanceHarnessTests`
- `compileJava compileTestJava`

## 交付记录

- 2026-05-23：需求创建，进入实现。
- 2026-05-23：落地 Gemini/Vertex `audio_translation` 与 `image_variation` native executor 路径；Anthropic 按官方能力面拆分为 Messages 图片输入理解已支持、audio 资源与图片生成类资源继续明确 blocked。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.GeminiAudioGatewayResourceExecutorTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GeminiImagesGatewayResourceExecutorTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests"`
- `.\gradlew.bat compileJava compileTestJava`
