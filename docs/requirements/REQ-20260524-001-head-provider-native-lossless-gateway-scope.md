# REQ-20260524-001 头部自有模型厂商 Native 与无损翻译网关范围

状态：In Progress  
日期：2026-05-24  
关联任务：[TASK-20260524-001](../../tasks/in-progress/TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)

## 背景

用户重新明确 x-ai-gateway 的最初愿景：客户端无论使用 OpenAI、Anthropic、Gemini 等头部厂商的哪一种 API 连接到网关，网关都应具备转发、路由和翻译能力。该目标不是追求所有长尾 provider 或所有官方非核心 API，而是围绕少数头部 AI 厂商建立可信的 native adapter 与资源属性无损翻译。

2026-05-24 进一步明确：进入支持范围的厂商 API 必须具备 native 能力；类似资源属性必须具备转换翻译能力；不可对应、不可无损转换或目标厂商不能 native 执行的能力必须直接失败，不能通过标记、header、metadata 或 local fake 让客户端误以为能力可用。Dify 这类非自有模型编排平台不应继续占用默认厂商目录；xAI、OpenAI、Anthropic、Gemini、MiMo、DeepSeek 等拥有自有模型或明确头部模型服务的厂商可以保留。

## 目标

- 将公开产品范围收敛为头部自有模型厂商和明确需要支持的头部模型服务。
- 为支持范围内的厂商建立 native API 能力要求，而不是仅依赖 generic OpenAI-compatible 包装。
- 建立跨协议资源属性无损翻译矩阵，覆盖 message、content part、tool、tool result、stream、usage、file/image/audio 等相似资源。
- 明确失败优先原则：不可对应能力直接返回错误，不返回降级成功、不做模糊标记、不提供 lossy fallback。
- 清理无意义或非自有模型厂商 preset、文档、能力矩阵和 smoke 范围，例如 Dify。
- 保留 xAI 及 OpenAI、Anthropic、Gemini/Vertex、MiMo、DeepSeek 等头部或自有模型厂商，并评估 Qwen、Moonshot、MiniMax、Mistral、Cohere、Jina、Perplexity 等是否符合保留标准。

## 非目标

- 不在本需求中立即修改代码或删除 provider preset。
- 不支持所有 OpenAI-compatible 聚合器、workflow 编排平台或无自有模型的中间层。
- 不追求 OpenAI、Anthropic、Gemini 或其它厂商的官方 API 全量覆盖。
- 不用 `local emulation`、`controlled translation` 或 header 标记来替代失败语义。
- 不把 gateway-local Files、Vector Stores、Conversations 等本地支撑能力伪装成厂商官方完整 lifecycle。

## 厂商范围初稿

### 必保留

- OpenAI
- Anthropic
- Gemini / Vertex
- MiMo
- DeepSeek
- xAI

### 候选保留

- Qwen
- Moonshot
- MiniMax
- Mistral
- Cohere
- Jina
- Perplexity

候选保留标准：拥有自有模型或明确头部模型服务；具备稳定公开 API；能够形成 native adapter 或明确 OpenAI-compatible native profile；有真实使用价值和可验证 smoke 路径。

### 默认清理

- Dify：定位为 workflow / application orchestration platform，不是自有基础模型厂商，不应作为默认模型 provider preset。
- OpenRouter、Together、Fireworks、SiliconFlow 等聚合或承载多第三方模型的平台：默认从核心支持范围移出；如保留，应作为非核心可选 preset，不进入核心兼容承诺。

## 能力设计原则

1. 同厂商入口到同厂商上游必须 native 执行。
2. 跨厂商翻译只允许无损翻译成功。
3. 无法无损表达的能力必须失败。
4. opaque state、encrypted reasoning、compaction、hosted tool lifecycle、provider-specific cache token 等不可复制能力不得本地伪造。
5. 客户端接收能力参差不齐，公开厂商兼容 API 不依赖 header 或 metadata 让客户端判断降级语义。
6. gateway-local 能力必须与厂商 official/native 能力区分，不作为官方兼容成功返回的替代品。

## 影响范围

- `src/main/resources/provider-catalog.json`
- provider preset loader、默认厂商导入和厂商管理 UI。
- protocol endpoint、conversation profile、capability matrix、conformance matrix。
- OpenAI/Anthropic/Gemini/MiMo/DeepSeek/xAI native adapter 与互转 mapper。
- public OpenAPI、`docs/public-api-compatibility.md`、`docs/functional-service-api-coverage-matrix.md`、SDK 示例和 smoke 文档。
- 既有 Responses compact、hosted file_search、Vector Store Search、Realtime、media 等边界口径。

## 风险

- 删除 provider preset 可能影响历史凭证或存量测试数据，需要迁移说明和兼容策略。
- “无损翻译”标准若定义过宽，会再次出现假成功风险。
- 如果把所有候选厂商都纳入必保留，native adapter 工作量会扩大并削弱主线闭环。
- 公开文档、provider catalog、UI 和测试 fixture 容易出现范围口径不一致。

## 验收标准

- 产出核心厂商保留/清理清单，Dify 等非模型 provider 不再出现在默认核心 preset。
- 每个保留厂商都有 native 能力要求和最小 smoke 入口。
- 每类跨协议资源属性都有无损翻译/失败边界。
- 不可对应能力统一失败，不返回官方成功对象或 local fake。
- public docs、OpenAPI、provider catalog、任务索引和测试边界同步更新。

## 测试边界

- 本需求阶段仅定义清单、边界和任务树，不运行代码测试。
- 后续子任务分别负责 catalog loader、adapter、translation matrix、错误语义、docs/OpenAPI 和 smoke 测试。

## 当前状态

- 2026-05-24：根据用户讨论创建需求草案，待按任务清单拆分实施。
- 2026-05-24：进入第一阶段实施，先闭环默认核心 provider catalog 收敛；保留 OpenAI、Azure OpenAI、MiMo、DeepSeek、Qwen、Moonshot、Volcengine、MiniMax、xAI、Perplexity、Cohere、Jina、Mistral、Anthropic、Gemini、Vertex，默认移除通用 generic preset、Dify、OpenRouter、SiliconFlow、Together、Fireworks。
- 2026-05-24：第一阶段默认 provider catalog 收敛已完成并通过针对性测试；后续继续推进 native adapter 最小契约、无损翻译矩阵、不可对应能力失败语义和公开文档/OpenAPI/smoke 对齐。
- 2026-05-24：不可对应能力失败语义开始落地。`POST /v1/responses/compact` 非 OpenAI Direct native route 已改为 HTTP 501 + `native_compaction_required`，不再返回本地 opaque marker `response.compaction`。
- 2026-05-24：native adapter 最小契约开始落地。默认核心 provider preset 已新增 `nativeAdapterContract`，公开记录 adapter kind、native surface、required endpoints、auth、stream、tools、usage、error mapping 和 smoke classification。
- 2026-05-24：无损翻译矩阵开始落地。已新增 `LosslessTranslationMatrixService` 作为第一阶段代码事实源，只允许 `LOSSLESS`、`NATIVE_REQUIRED`、`UNSUPPORTED` 三类结果；未声明属性默认失败，opaque/native-only 属性不得通过 local emulation 成功返回。
- 2026-05-24：无损翻译矩阵已接入 `TranslationExecutionPlanCompiler`。跨协议请求如果命中 `NATIVE_REQUIRED` 或 `UNSUPPORTED` 属性，会进入 `BLOCKED` 执行计划并暴露明确 failure code；同协议 native/openai-compatible 路径不会被误判为翻译。
- 2026-05-24：conformance baseline 已同步新失败语义：OpenAI surface 请求 Gemini native 图片编辑、图片变体、音频翻译，以及 OpenAI surface 请求 Anthropic file object create/get/content/delete，均改为 `BLOCKED`；Google native 自身 file/image/audio 路径和本地 file list 保持 native/orchestration 能力边界。
- 2026-05-24：公开文档第一阶段已同步 native + 无损翻译口径。`docs/public-api-compatibility.md`、public docs bundle 与 OpenAPI snapshot 已明确默认核心 provider 清单、移除 Dify/OpenRouter/Together/Fireworks/SiliconFlow/generic 的默认核心承诺，并公开 `native_route_required`、`unsupported_translation_attribute`、`native_compaction_required`、`native_image_edit_required`、`native_image_variation_required`、`native_audio_translation_required` 等失败语义。
