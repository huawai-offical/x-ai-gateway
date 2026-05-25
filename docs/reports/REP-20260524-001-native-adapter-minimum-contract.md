# REP-20260524-001 Native Adapter 最小契约

日期：2026-05-24  
关联需求：[REQ-20260524-001](../requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md)  
关联任务：[TASK-20260524-001-02](../../tasks/done/TASK-20260524-001-02-native-adapter-minimum-contract.md)

## 结论

默认核心 provider preset 必须声明 `nativeAdapterContract`，并至少包含：

- `adapterKind`：厂商适配器类型，例如 `openai_direct`、`anthropic_direct`、`gemini_direct`、`provider_specific_openai_compatible`。
- `nativeSurface`：真实上游 API 面，例如 `responses_and_chat_completions`、`messages`、`generate_content`、`chat_completions`。
- `nativeProtocols`：该厂商可 native 执行的协议族。
- `requiredEndpoints`：最小 smoke / contract 必须覆盖的上游 endpoint。
- `auth`、`stream`、`tools`、`usage`、`errorMapping`：运行时不可模糊推断的协议属性。
- `smokeClassification`：当前默认统一为 `native_required`，表示不能用 local fake success 替代。

这里的 `provider_specific_openai_compatible` 是具名自有模型厂商的 native profile 描述，不是 generic OpenAI-compatible 成功承诺。只有厂商身份、native/profile contract、capability snapshot、smoke 证据和无损翻译矩阵同时成立时，网关才可返回成功。

## 当前默认核心契约

| Provider | adapterKind | nativeSurface | 关键边界 |
| --- | --- | --- | --- |
| OpenAI | `openai_direct` | `responses_and_chat_completions` | Responses / Chat 原生执行，错误和 usage 透传。 |
| Azure OpenAI | `azure_openai_deployment` | `deployment_chat_completions` | deployment 寻址是 native 契约的一部分。 |
| MiMo | `provider_specific_openai_compatible` | `chat_completions` | MiMo provider-specific profile；Responses ingress 只能无损转 Chat Completions，否则失败。 |
| DeepSeek | `provider_specific_openai_compatible` | `chat_completions` | reasoning/chat 走 provider-specific OpenAI-compatible profile。 |
| Qwen | `provider_specific_openai_compatible` | `chat_completions` | DashScope compatible mode，不再混同 generic preset。 |
| Moonshot | `provider_specific_openai_compatible` | `chat_completions` | 长上下文 chat native profile。 |
| Volcengine | `provider_specific_openai_compatible` | `chat_completions_path_adapter` | `/api/v3` path adapter 是 contract 要求。 |
| MiniMax | `provider_specific_openai_compatible` | `chat_completions` | 中文 chat provider native profile。 |
| xAI | `provider_specific_openai_compatible` | `chat_completions` | xAI-compatible chat surface 保留；Responses 需无损转译或失败。 |
| Perplexity | `provider_specific_openai_compatible` | `web_grounded_chat_completions` | web_search/chat 不泛化为全量 OpenAI lifecycle。 |
| Cohere | `cohere_native` | `embed_and_rerank` | native embed/rerank，不声明 chat lifecycle。 |
| Jina | `jina_native` | `embed_and_rerank` | native embeddings/rerank，不声明 chat lifecycle。 |
| Mistral | `provider_specific_openai_compatible` | `chat_completions_path_adapter` | path adapter 与工具/usage 边界需明确。 |
| Anthropic | `anthropic_direct` | `messages` | OpenAI/Gemini ingress 必须无损映射到 Messages，否则失败。 |
| Gemini | `gemini_direct` | `generate_content` | OpenAI/Anthropic ingress 必须无损映射到 generateContent，否则失败。 |
| Vertex | `vertex_ai_gemini` | `generate_content_with_project_location` | project/location/bearer 寻址是 native 契约的一部分。 |

## 当前实现审计

当前 `nativeAdapterContract` 已经成为 catalog 事实源，但它不等同于每个厂商都已经具备独立 runtime executor。现阶段运行时仍按粗粒度 `ProviderType` 执行：

| 范围 | 当前 runtime 状态 | 需要继续闭环 |
| --- | --- | --- |
| OpenAI Direct | `OPENAI_DIRECT`，已有 OpenAI Chat/Responses/资源族执行路径。 | `/responses/compact` 仅 OpenAI Direct 或目标 native 等价能力可成功，非 native route 必须明确失败。 |
| Anthropic Direct | `ANTHROPIC_DIRECT`，已有 Messages adapter 与 usage normalizer。 | 补齐 file/tool/reasoning 的 native contract smoke，禁止 OpenAI file lifecycle 自动映射为成功。 |
| Gemini / Vertex | `GEMINI_DIRECT` + `UpstreamSiteKind.GEMINI_DIRECT` / `VERTEX_AI`，已有 generateContent 与部分 Google native namespace。 | Vertex 仍复用 Gemini provider type，需要将 project/location/bearer 寻址作为 contract test 固化。 |
| MiMo / DeepSeek / xAI / Qwen / Moonshot / Volcengine / MiniMax / Mistral / Perplexity | catalog 已区分 provider-specific OpenAI-compatible contract；runtime provider key 已可派生具名身份；functional smoke dry-run 已按 provider-specific protocol 和 contract endpoint 生成。 | 仍不扩展为全量 OpenAI lifecycle；能力成功需继续受 capability snapshot、Lossless Translation Matrix 和 hard-fail 规则约束。 |
| Cohere / Jina | catalog 声明 native embed/rerank contract；`TASK-20260524-001-07` 已补 resource-level native executor、smoke/record-replay 分类、独立 fixture 样本与结构证据切片。 | 任务未归档前继续补真实 key live smoke；继续用 focused tests 验证 `/v1/embeddings`、`/v1/rerank` 与 chat/files/uploads 分离，不能把 chat 兼容能力作为默认假设。 |

## Smoke / Fixture 口径

功能性 provider smoke 已从“泛型 compatible”进一步收敛为 provider-specific 证据：

- Gemini native smoke 使用 `GEMINI_DIRECT` / `GEMINI_NATIVE`。
- MiMo official smoke 使用 `XIAOMI_MIMO` / `XIAOMI_MIMO_OPENAI_COMPATIBLE` / `XIAOMI_MIMO_ANTHROPIC_COMPATIBLE`。
- `mimo_openai` / `mimo_anthropic` 是 MiMo provider-specific profile；`openai_compatible` / `anthropic_compatible` 只作为旧请求 alias 保留，只有 MiMo baseUrl/profile 才会归一到 `XIAOMI_MIMO_*`。
- DeepSeek、xAI、Qwen、Moonshot、Volcengine、MiniMax、Mistral、Perplexity 等其它自有厂商 compatible 入口不能被 functional provider smoke 或 record/replay fixture 伪装成 MiMo native 证据；dry-run 与 record/replay 使用各自 provider-specific protocol，并按 catalog `requiredEndpoints` 生成 path。
- Cohere/Jina official smoke 必须使用 `COHERE_NATIVE` / `JINA_NATIVE` provider-specific protocol，只允许 `EMBEDDINGS`、`RERANK` 家族进入 PASS/FAIL/UNSUPPORTED 分类；chat/files/uploads 不作为 Cohere/Jina 成功证据。
- 离线 `FunctionalProviderSmokeRecordReplayFixtureVerifier` 只允许核心 provider fixture provider/protocol，不允许 `DIFY`、`OPENROUTER`、`TOGETHER`、`FIREWORKS`、`SILICONFLOW`、`OPENAI_COMPATIBLE_GENERIC` 或顶层 `OPENAI_COMPATIBLE` 进入 official smoke fixture。

## 已发现缺口

- `ProviderType` 仍只有 `OPENAI_DIRECT`、`OPENAI_COMPATIBLE`、`ANTHROPIC_DIRECT`、`GEMINI_DIRECT`、`OLLAMA_DIRECT`，无法直接表达 MiMo、DeepSeek、xAI 等自有模型厂商的独立执行身份。
- `UpstreamSiteKind` 能区分具名厂商，但部分支持矩阵仍把 `OPENAI_COMPATIBLE_GENERIC`、SiliconFlow、Together、Fireworks、OpenRouter 等旧长尾站点列入 OpenAI-style resource 支持判断，需继续收敛到默认核心清单。
- 旧能力层仍存在 `InteropCapabilityLevel.EMULATED` / `LOSSY`，可作为历史展示或观测状态，但不能作为 REQ-20260524-001 的跨协议资源属性翻译成功依据。
- `responses.emulated` catalog hint 已从默认 provider catalog 与 fallback catalog 迁移为 `responses.native` 或 `responses.lossless-translation`；`responsesCompatibility.mode` 已使用 `lossless_to_chat_completions`，运行时不再接受旧 `emulate_with_chat_completions` 模式作为成功开关。历史配置值不能继续表示 `/responses/compact` 或其它官方能力的成功路径。

## 验收证据

- `ProviderCatalogLoaderTests` 要求每个默认 preset 都包含结构化 `nativeAdapterContract`。
- `PublicDocsBundleServiceTests` 验证公开 docs bundle 暴露 xAI、Anthropic、Gemini 的 native contract。
- Admin preset response 已透出 `nativeAdapterContract`，后续 UI / smoke harness 可直接读取同一事实源。
- `EmbedRerankNativeGatewayResourceExecutorTests` 验证 Cohere `/v2/embed`、Jina `/v1/rerank`、Cohere `compatibility/v1` hard-fail 与非 embed/rerank 语义不接管。
- `EmbedRerankNativeGatewayResourceExecutorTests` 进一步验证 Cohere embed response 的 `embeddings.float`、`meta.billed_units.input_tokens`，以及 Cohere rerank response 的 `results[].relevance_score`、`meta.billed_units.search_units`，避免只凭 HTTP 200 宣称 native 成功。
- `FunctionalProviderSmokeHttpClientTests` 验证 provider-specific OpenAI-compatible dry-run path 与 catalog `nativeAdapterContract.requiredEndpoints` 一致，覆盖 Qwen `/compatible-mode/v1/chat/completions`、Volcengine `/api/v3/chat/completions`、Perplexity `/chat/completions` 等 path adapter。
- `FunctionalProviderSmokeHttpClientTests` 与 `FunctionalProviderSmokeRecordReplayFixtureVerifierTests` 验证 `COHERE_NATIVE` / `JINA_NATIVE` dry-run、live probe 分类、Bearer 脱敏、chat path 拒绝、独立 sample fixture 和非 embed/rerank family 成功禁入；record/replay verifier 也只接受核心 provider-specific protocol。

## 后续承接

- `TASK-20260524-001-03` 已完成 `translationBoundary`、无损翻译矩阵、mapper negative tests 与 UNSUPPORTED smoke 样本闭环。
- Cohere/Jina fixture 样本与本地结构证据已由 `TASK-20260524-001-07` 补齐；真实 key live smoke 仍由 `TASK-20260524-001-07` 承接，当前环境未发现 `COHERE` / `JINA` 相关环境变量名，不能用模拟结果替代。

## 归档结论

2026-05-24：本报告对应的 `TASK-20260524-001-02` 已完成归档。native adapter 最小契约已落到 provider catalog 事实源，并通过 Admin/Public 响应透出；provider-specific OpenAI-compatible 厂商的 smoke dry-run、record/replay verifier 与 catalog `requiredEndpoints` 已完成 contract drift 对齐。剩余工作不再归属本任务：跨协议无损 mapper negative tests 和 UNSUPPORTED smoke 样本由 `TASK-20260524-001-03` 承接；Cohere/Jina fixture 样本已由 `TASK-20260524-001-07` 补齐，真实 key live smoke 仍由 `TASK-20260524-001-07` 承接。
