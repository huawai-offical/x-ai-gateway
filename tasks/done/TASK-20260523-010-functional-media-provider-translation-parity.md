# TASK-20260523-010 功能性媒体 API 厂商互转补齐

状态：Done  
优先级：P0  
类型：父任务 + 子任务  
来源：`docs/requirements/REQ-20260523-009-functional-media-provider-translation-parity.md`

## 背景

用户确认系统只做功能性 API 的实现和互转，不做 `admin`、`tuning`、`evals`、provider batch/job/pipeline 等官方全量 API。当前需要继续完善 Anthropic 的 audio/image，以及 Google Gemini 的 `audio_translation` 和 `image_variation`。

## 目标

- 把功能性媒体 API 的厂商互转能力继续推进到执行器和能力矩阵。
- Gemini/Vertex `audio_translation`、`image_variation` 从 blocked 改为可执行互转。
- Anthropic audio/image 拆成可互转与不可互转边界：Messages 图片输入理解可用，audio 资源与图片生成类资源明确 blocked。
- 回写公开兼容文档和测试证据。

## 非目标

- 不实现任何官方非功能性 API。
- 不为无音频输出能力的厂商伪造 `/v1/audio/speech`。
- 不为无图像生成能力的厂商伪造 image generation/edit/variation。
- 不执行真实上游 smoke；真实凭证和成本预算另行排期。

## 上游来源

- 用户要求：继续完善 Anthropic 的 audio/image、Google Gemini 的 audio translation 和 image variation。
- 需求文档：`docs/requirements/REQ-20260523-009-functional-media-provider-translation-parity.md`
- 已完成任务：`tasks/done/TASK-20260523-005-03-provider-native-media-adapter-parity.md`

## 输入

- `GeminiAudioGatewayResourceExecutor`
- `GeminiImagesGatewayResourceExecutor`
- `AnthropicNativeGatewayChatRuntime`
- `SiteCapabilityTruthService`
- `ExecutionSupportMatrixService`
- conformance fixtures

## 输出

- Gemini/Vertex audio translation executor 支持。
- Gemini/Vertex image variation executor 支持。
- Anthropic audio/image 能力边界更新，不伪造无上游等价能力的资源输出。
- 测试、文档和任务归档。

## 影响范围

- gateway resource executor。
- capability matrix 和 conformance fixture。
- 公开兼容文档。

## 依赖

- 已有 OpenAI-style audio/images ingress。
- 已有 Gemini native audio/image executor。
- 已有 Anthropic Messages runtime 和 file binding。

## 风险

- 厂商多模态输入/输出能力存在模型差异。
- 互转能力必须和 native 能力分开标记，避免误导用户。
- 不支持能力仍需明确失败，不能隐式 fallback。

## 子任务

### TASK-20260523-010-01 Gemini audio_translation 与 image_variation 互转

状态：Done  
边界：仅覆盖 Gemini/Vertex 原生互转，不改 OpenAI-compatible passthrough。  
输出：执行器、能力矩阵、conformance fixture、测试。  
验证：Gemini executor 与 interop 聚焦测试。

### TASK-20260523-010-02 Anthropic audio/image 功能性边界与互转

状态：Done  
边界：仅覆盖可映射到 Messages/files 的功能性媒体输入；不实现无上游能力的生成类输出。  
输出：能力判定更新、blocked reason 收敛、测试。  
验证：Anthropic 相关 execution/interop 聚焦测试。

### TASK-20260523-010-03 文档与任务回写

状态：Done  
边界：更新需求、公开兼容说明、任务状态和遗留问题。  
输出：Done 任务与验证记录。  
验证：索引链接和测试命令记录。

## 验收标准

- Gemini/Vertex `audio_translation` 可执行。
- Gemini/Vertex `image_variation` 可执行。
- Anthropic audio/image 能力展示不再一概 blocked：图片输入理解归 Messages 多模态，audio 资源与图片生成/编辑/variation 保留明确 blocked reason。
- 不支持项保留明确原因。
- 相关测试通过。

## 测试边界

- 单元测试和 conformance fixture。
- 不访问真实 Gemini/Anthropic 网络服务。

## 关联文档

- `docs/requirements/REQ-20260523-009-functional-media-provider-translation-parity.md`
- `docs/requirements/REQ-20260523-004-provider-audio-file-image-resource-coverage.md`

## 当前状态

- 2026-05-23：任务创建，开始复核当前执行器和能力边界。
- 2026-05-23：已实现 Gemini/Vertex `audio_translation` 与 `image_variation` executor 分支；已更新 capability truth、support matrix、site policy、conformance fixture 和 Anthropic blocked reason。
- 2026-05-23：聚焦测试与编译通过，任务归档到 Done。

## 验证记录

- `.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.GeminiAudioGatewayResourceExecutorTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GeminiImagesGatewayResourceExecutorTests" --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteCapabilityTruthServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests"`
- `.\gradlew.bat compileJava compileTestJava`

## 遗留问题

- Anthropic 当前仅保留 Messages 图片输入理解与 file 支撑；audio 资源和图片生成/编辑/variation 没有稳定上游等价契约，继续明确 blocked。
