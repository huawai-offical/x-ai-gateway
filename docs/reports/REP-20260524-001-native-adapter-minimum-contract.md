# REP-20260524-001 Native Adapter 最小契约

日期：2026-05-24  
关联需求：[REQ-20260524-001](../requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)  
关联任务：[TASK-20260524-001-02](../../tasks/in-progress/TASK-20260524-001-02-native-adapter-minimum-contract.md)

## 结论

默认核心 provider preset 必须声明 `nativeAdapterContract`，并至少包含：

- `adapterKind`：厂商适配器类型，例如 `openai_direct`、`anthropic_direct`、`gemini_direct`、`provider_specific_openai_compatible`。
- `nativeSurface`：真实上游 API 面，例如 `responses_and_chat_completions`、`messages`、`generate_content`、`chat_completions`。
- `nativeProtocols`：该厂商可 native 执行的协议族。
- `requiredEndpoints`：最小 smoke / contract 必须覆盖的上游 endpoint。
- `auth`、`stream`、`tools`、`usage`、`errorMapping`：运行时不可模糊推断的协议属性。
- `smokeClassification`：当前默认统一为 `native_required`，表示不能用 local fake success 替代。

## 当前默认核心契约

| Provider | adapterKind | nativeSurface | 关键边界 |
| --- | --- | --- | --- |
| OpenAI | `openai_direct` | `responses_and_chat_completions` | Responses / Chat 原生执行，错误和 usage 透传。 |
| Azure OpenAI | `azure_openai_deployment` | `deployment_chat_completions` | deployment 寻址是 native 契约的一部分。 |
| MiMo | `provider_specific_openai_compatible` | `chat_completions` | Responses ingress 只能无损转 Chat Completions，否则失败。 |
| DeepSeek | `provider_specific_openai_compatible` | `chat_completions` | reasoning/chat 走 provider-specific OpenAI-compatible profile。 |
| Qwen | `provider_specific_openai_compatible` | `chat_completions` | DashScope compatible mode，不再混同 generic preset。 |
| Moonshot | `provider_specific_openai_compatible` | `chat_completions` | 长上下文 chat native profile。 |
| Volcengine | `provider_specific_openai_compatible` | `chat_completions_path_adapter` | `/api/v3` path adapter 是 contract 要求。 |
| MiniMax | `provider_specific_openai_compatible` | `chat_completions` | 中文 chat provider native profile。 |
| xAI | `provider_specific_openai_compatible` | `chat_completions` | xAI-compatible chat surface；Responses 需无损转译或失败。 |
| Perplexity | `provider_specific_openai_compatible` | `web_grounded_chat_completions` | web_search/chat 不泛化为全量 OpenAI lifecycle。 |
| Cohere | `cohere_native` | `embed_and_rerank` | native embed/rerank，不声明 chat lifecycle。 |
| Jina | `jina_native` | `embed_and_rerank` | native embeddings/rerank，不声明 chat lifecycle。 |
| Mistral | `provider_specific_openai_compatible` | `chat_completions_path_adapter` | path adapter 与工具/usage 边界需明确。 |
| Anthropic | `anthropic_direct` | `messages` | OpenAI/Gemini ingress 必须无损映射到 Messages，否则失败。 |
| Gemini | `gemini_direct` | `generate_content` | OpenAI/Anthropic ingress 必须无损映射到 generateContent，否则失败。 |
| Vertex | `vertex_ai_gemini` | `generate_content_with_project_location` | project/location/bearer 寻址是 native 契约的一部分。 |

## 验收证据

- `ProviderCatalogLoaderTests` 要求每个默认 preset 都包含结构化 `nativeAdapterContract`。
- `PublicDocsBundleServiceTests` 验证公开 docs bundle 暴露 xAI、Anthropic、Gemini 的 native contract。
- Admin preset response 已透出 `nativeAdapterContract`，后续 UI / smoke harness 可直接读取同一事实源。

## 后续

- 将 `nativeAdapterContract.smokeClassification` 接入 smoke harness PASS/FAIL/UNSUPPORTED 分类。
- 在 `TASK-20260524-001-03` 中把 `translationBoundary` 与无损翻译矩阵联动。
- 在 `TASK-20260524-001-04` 中继续清理会绕过 contract 的 local fake success。
