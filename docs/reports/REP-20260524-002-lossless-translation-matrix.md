# REP-20260524-002 跨协议资源属性无损翻译矩阵

日期：2026-05-24  
关联需求：[REQ-20260524-001](../requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)  
关联任务：[TASK-20260524-001-03](../../tasks/in-progress/TASK-20260524-001-03-lossless-translation-matrix.md)

## 背景

用户明确要求：支持范围内的厂商 API 必须具备 native 能力；相似资源属性只有在可以无损表达时才能翻译；不可对应能力必须直接失败，不能用 local emulation、lossy fallback、header、metadata 或 warning 让客户端误以为返回可用。

本报告记录第一阶段矩阵事实源。代码事实源位于 `LosslessTranslationMatrixService`，只使用三种状态：

- `LOSSLESS`：可以跨协议无损翻译。
- `NATIVE_REQUIRED`：只能走目标厂商 native route；作为跨协议翻译请求时必须失败。
- `UNSUPPORTED`：矩阵未声明可翻译或明确不可等价；必须失败。

## 第一阶段覆盖范围

| 资源属性 | 分类 | 说明 |
| --- | --- | --- |
| `message.role` | `LOSSLESS` | OpenAI、Responses、Anthropic、Gemini 基础角色可通过 canonical 表达。 |
| `content.text` | `LOSSLESS` | 文本内容可无损表达。 |
| `content.image.inline_data` | `LOSSLESS` | inline image data 可保留 mime/body。 |
| `content.image.remote_url` | `NATIVE_REQUIRED` | 远程 URL 的鉴权、抓取和缓存语义不保证跨厂商等价。 |
| `content.file.inline_data` | `LOSSLESS` | inline file data 可保留 mime/body。 |
| `content.file.provider_file_id` | `NATIVE_REQUIRED` | provider file id 只在原厂对象生命周期内有效。 |
| `tool.function_schema.basic_object` | `LOSSLESS` / `UNSUPPORTED` | OpenAI/Anthropic 基础 object schema 可保守互转；Gemini functionDeclarations 不按全量 JSON Schema 等价承诺。 |
| `tool_result.text` | `LOSSLESS` | OpenAI/Anthropic 文本工具结果可保守互转。 |
| `tool_result.call_id` | `UNSUPPORTED` | Gemini functionResponse 不能保留 OpenAI/Anthropic tool call id 语义。 |
| `stream.text_delta` | `LOSSLESS` | 文本增量可通过 canonical stream delta 表达。 |
| `stream.tool_call_delta` | `NATIVE_REQUIRED` | 增量 tool call 状态机跨厂商不同。 |
| `usage.input_output_tokens` | `LOSSLESS` | 输入/输出 token 计数可作为普通 usage 字段传递。 |
| `usage.cache_tokens` | `NATIVE_REQUIRED` | cache token、prompt cache 和计费细节只允许 native 暴露。 |
| `reasoning.thinking_budget` | `NATIVE_REQUIRED` | reasoning/thinking 配置只允许目标厂商 native profile 执行。 |
| `reasoning.encrypted_content` | `NATIVE_REQUIRED` | encrypted reasoning 是 opaque provider state，不能本地重建或翻译。 |
| `response.compaction` | `NATIVE_REQUIRED` | `/v1/responses/compact` 必须走 OpenAI Direct native route，失败码 `native_compaction_required`。 |
| `response.hosted_tool.file_search` | `NATIVE_REQUIRED` | hosted tool lifecycle 不跨厂商翻译。 |
| `file.object_lifecycle` | `NATIVE_REQUIRED` | file object id、状态机和内容读取必须由原厂 native lifecycle 承担。 |
| `upload.multipart_lifecycle` | `NATIVE_REQUIRED` | multipart upload 状态机不跨厂商翻译。 |
| `image.generation.request` | `NATIVE_REQUIRED` | 图片生成参数、返回对象和安全元数据不声明跨厂商无损。 |
| `image.edit.request` | `NATIVE_REQUIRED` | 图片编辑参数、mask 和返回对象不声明跨厂商无损。 |
| `image.variation.request` | `NATIVE_REQUIRED` | 图片变体参数、输入对象和返回对象不声明跨厂商无损。 |
| `audio.*.request` | `NATIVE_REQUIRED` | 音频请求、segment、logprob、voice 和格式元数据不声明跨厂商无损。 |
| `web_search.grounded_sources` | `NATIVE_REQUIRED` | grounded source/citation contract 只按原厂或明确 provider profile 暴露。 |

## 失败优先规则

- 未出现在矩阵中的属性默认 `UNSUPPORTED`，错误码 `unsupported_translation_attribute`。
- source protocol 与 target protocol 相同的请求不应被当成翻译；应走 native route，分类为 `NATIVE_REQUIRED`。
- 矩阵不包含 `LOSSY` 或 `EMULATED` 状态；历史能力层里的 degraded 语义不能作为跨协议属性翻译成功条件。
- `NATIVE_REQUIRED` 不是“可用但降级”，而是“只有 native route 可执行；翻译路径必须失败”。

## 执行计划接入

- `TranslationExecutionPlanCompiler` 已读取矩阵结果，把 `NATIVE_REQUIRED` / `UNSUPPORTED` 转成 `blockerReasons`、`ExecutionKind.BLOCKED`、`SupportStatus.BLOCKED` 与 `degradationLevel=UNSUPPORTED`。
- `targetProtocol` 会区分同协议 native route 与真正跨协议翻译；OpenAI-compatible/head provider 如果支持当前 ingress protocol，不会被误判为跨协议转换。
- 请求体属性采集只在会话资源上递归读取 message/content/tool/reasoning 等属性；image/audio/file 等非会话资源只按 operation 级属性判断，避免把 `model` 或 `prompt` 错归为 `content.text`。
- `GatewayResourceExecutionService` 已在 JSON、binary、multipart 资源执行入口统一执行 `ensureExecutable`；如果 planner 产出 `BLOCKED` plan，会在调用上游 credential/executor 前失败，并记录 lifecycle failure，不把该逻辑阻断计入上游凭证 cooldown。
- conformance baseline 已覆盖 OpenAI surface 到 Gemini native 的图片编辑、图片变体、音频翻译硬失败，以及 OpenAI surface 到 Anthropic file object lifecycle 的硬失败；Google native 自身资源路径保持 native/orchestration。

## 后续接入点

- 在 OpenAI/Anthropic/Gemini request mapper 中对 provider file id、encrypted reasoning、tool call delta 等属性补 negative tests。
- public docs/OpenAPI 第一阶段已引用矩阵分类，不再使用模糊的 degraded 或 emulated 口径；后续继续补 SDK 示例与 smoke harness 范围。

## 验证

第一阶段新增 `LosslessTranslationMatrixServiceTests`，覆盖：

- 基础 message role/text 的 `LOSSLESS` 分类。
- compact、encrypted reasoning、provider file id 的 `NATIVE_REQUIRED` 分类。
- 未声明属性默认 `UNSUPPORTED`。
- 同协议请求分类为 `NATIVE_REQUIRED`。
- 请求体级属性识别会阻断 provider file id，且非会话资源 prompt 不会被误识别为聊天文本。
- 执行计划编译器会把 matrix blocker 转成 `BLOCKED` 计划，并保留同协议 native route。
- 资源执行服务会在 blocked plan 下拒绝执行，不解析上游凭证、不调用 executor、不触发 credential cooldown，并写入失败 lifecycle。
- public docs bundle 和 OpenAPI snapshot 会公开 Lossless Translation Matrix、native-required 失败码和默认核心 provider 清理口径。
- 矩阵状态不出现 `LOSSY` 或 `EMULATED`。

已通过验证命令：

```powershell
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.LosslessTranslationMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayRequestFeatureServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.ExecutionSupportMatrixServiceTests"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.NonChatDegradationPolicyServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.GatewayInteropPlanServiceTests"
.\gradlew.bat test --tests "com.prodigalgal.xaigateway.gateway.core.execution.GatewayResourceExecutionServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.LosslessTranslationMatrixServiceTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.TranslationExecutionPlanCompilerLosslessMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.EndpointConformanceMatrixTests" --tests "com.prodigalgal.xaigateway.gateway.core.interop.SiteConformanceHarnessTests"
```
