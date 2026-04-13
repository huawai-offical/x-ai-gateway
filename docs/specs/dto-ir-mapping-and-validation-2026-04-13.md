# X-18：全协议 DTO -> IR 映射与校验规范事实来源（2026-04-13）

## 背景
- 当前项目已经具备多条协议入口和资源入口，但协议解析、校验和目标 IR 语义分散在多个 controller 与 service 中。
- 本文作为仓库内事实来源，和 Notion 设计页一起定义“入口 DTO / 原始载荷 -> 统一 IR 家族”的映射、拒绝条件和测试锚点。

## IR 家族
- `chat execution`
  - 目标对象：`ChatExecutionRequest`
  - 入口：`OpenAiChatCompletionRequestMapper`、`OpenAiResponsesRequestMapper`、`AnthropicMessagesRequestMapper`、`GeminiGenerateContentRequestMapper`
- `resource execution`
  - 目标对象：`GatewayResourceExecutionContext`
  - 入口：`OpenAiEmbeddingsController`、`OpenAiAudioController`、`OpenAiImagesController`、`OpenAiModerationsController`、`OpenAiUploadsController`、`OpenAiBatchesController`、`OpenAiFineTuningJobsController`、`OpenAiRealtimeController`
- `direct file`
  - 目标服务：`GatewayFileService`
  - 入口：`OpenAiFilesController`
- `direct query / direct async`
  - 目标服务：`ModelCatalogQueryService`、`GatewayAsyncResourceService`
  - 入口：`OpenAiModelsController`、`OpenAiResponsesController` 的 GET/DELETE

## 聊天入口映射
### OpenAI Chat Completions
- DTO：`OpenAiChatCompletionRequest`
- 目标 IR：`ChatExecutionRequest`
- 核心映射：
  - `model` -> `requestedModel`
  - `messages[*].role/content` -> `MessageInput`
  - `tools` -> `GatewayToolDefinition`
  - `tool_choice` -> `toolChoice`
  - `reasoning` / `reasoning_effort` -> `executionMetadata`
- 文件/图片：
  - `image_url` -> `MediaInput(kind=image)`
  - `input_file.file_id` -> `MediaInput(url=gateway://{fileId})`
  - `input_file.url/file_url` -> `MediaInput(kind=file)`
- 拒绝条件：
  - 没有任何可用 `user` 消息
- 测试锚点：
  - `OpenAiChatCompletionsControllerTests.shouldRejectOpenAiCompletionWithoutUserMessage`

### OpenAI Responses
- DTO：原始 `JsonNode`
- 目标 IR：`ChatExecutionRequest`
- 核心映射：
  - `instructions` -> system message
  - `input` 字符串 / object / array -> `MessageInput`
  - `function_call_output` -> `MessageInput(role=tool, toolCallId=call_id)`
  - `tools` -> `GatewayToolDefinition`
  - `tool_choice` / `temperature` / `max_output_tokens` -> 对应字段
  - 完整原始 body -> `executionMetadata`
- 文件/图片：
  - `input_image.file_id` -> `gateway://fileId`
  - `input_file.file_id` -> `gateway://fileId`
  - `input_image.image_url` / `input_file.url|file_url` -> 外部 URL
- 拒绝条件：
  - `function_call_output` 缺少 `call_id`
  - 不存在任何 user 输入或 `function_call_output`
  - `input` 不是 string/object/array
- 测试锚点：
  - `OpenAiResponsesControllerTests.shouldRejectFunctionCallOutputWithoutCallId`

### Anthropic Messages
- DTO：`AnthropicMessagesRequest`
- 目标 IR：`ChatExecutionRequest`
- 核心映射：
  - `system` -> system message
  - `messages[*].content` 文本 / image / document / tool_result -> `MessageInput`
  - `tools[*]` -> `GatewayToolDefinition`
  - `toolChoice` / `temperature` / `maxTokens` -> 对应字段
- 文件/图片：
  - `document.source.file_id` / `image.source.file_id` -> `gateway://fileId`
  - `document.source.url|uri` / `image.source.url|uri` -> 外部 URL
- 拒绝条件：
  - 没有任何可用 `user` 消息
- 测试锚点：
  - `AnthropicMessagesControllerTests.shouldRejectAnthropicMessageWithoutUserPayload`

### Gemini GenerateContent
- DTO：`GeminiGenerateContentRequest`
- 目标 IR：`ChatExecutionRequest`
- 核心映射：
  - `systemInstruction` -> system message
  - `contents[*].parts[*].text` -> `MessageInput.content`
  - `functionResponse` -> `MessageInput(role=tool)`
  - `tools[*].functionDeclarations[*]` -> `GatewayToolDefinition`
  - `generationConfig.temperature/maxOutputTokens` -> 对应字段
- 文件/图片：
  - `fileData.fileId` -> `gateway://fileId`
  - `fileData.fileUri` -> 外部 URL
- 拒绝条件：
  - 没有任何带文本或媒体的 `user` 内容
- 测试锚点：
  - `GeminiGenerateContentControllerTests.shouldRejectGeminiRequestWithoutUserPayload`

## 资源入口归档
### Passthrough JSON
- 入口：`embeddings`、`images`、`moderations`、部分 `realtime`
- 目标：`GatewayResourceExecutionContext`
- 统一要求：
  - 请求体必须是 JSON object
  - 必须能解析出 `model`，否则用 `defaultModel`
  - 资源执行不会进入 `ChatExecutionRequest`

### Multipart
- 入口：`audio/transcriptions`、`audio/translations`、`uploads`、部分 `batches`
- 目标：`GatewayResourceExecutionContext` + `GatewayResourceExecutor.executeMultipart`
- 统一要求：
  - 表单字段组装为 `routePayload`
  - 文件部分仅保留为 multipart，不映射到聊天 IR

### Binary
- 入口：`audio/speech`
- 目标：`GatewayResourceExecutionContext` + `GatewayResourceExecutor.executeBinary`
- 统一要求：
  - JSON body 必须可解析为 object
  - 执行链可在首包前 fallback

### Direct File / Query / Async
- `OpenAiFilesController`：直接落 `GatewayFileService`
- `OpenAiModelsController`：直接落 `ModelCatalogQueryService`
- `OpenAiResponsesController` GET/DELETE：直接落 `GatewayAsyncResourceService`

## 统一错误语义
- 鉴权失败：`GatewayUnauthorizedException` -> `UNAUTHORIZED`
- 校验失败：`IllegalArgumentException` -> `INVALID_ARGUMENT`
- 未找到资源：`ApiResourceNotFoundException` -> `NOT_FOUND`
- 其他未处理异常：`Exception` -> `INTERNAL_ERROR`

## 关键代码锚点
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionRequestMapper.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesRequestMapper.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/anthropic/AnthropicMessagesRequestMapper.java`
- `src/main/java/com/prodigalgal/xaigateway/protocol/ingress/google/GeminiGenerateContentRequestMapper.java`
- `src/main/java/com/prodigalgal/xaigateway/gateway/core/execution/GatewayResourceExecutionService.java`

## 关键测试锚点
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiChatCompletionsControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/openai/OpenAiResponsesControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/anthropic/AnthropicMessagesControllerTests.java`
- `src/test/java/com/prodigalgal/xaigateway/protocol/ingress/google/GeminiGenerateContentControllerTests.java`
