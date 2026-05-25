# REQ-20260524-001 头部自有模型厂商 Native 与无损翻译网关范围

状态：In Progress  
日期：2026-05-24  
关联任务：[TASK-20260524-001](../../tasks/in-progress/TASK-20260524-001-head-provider-native-lossless-gateway-parent.md)

## 背景

用户重新明确 x-ai-gateway 的最初愿景：客户端无论使用 OpenAI、Anthropic、Gemini、MiMo、DeepSeek、xAI 等头部自研大模型厂商的哪一种 API 连接到网关，网关都应具备转发、路由和翻译能力。该目标不是追求所有长尾 provider、聚合器、workflow 编排平台或所有官方非核心 API，而是围绕少数头部 AI 厂商建立可信的 native adapter / provider-specific native profile 与资源属性无损翻译。

2026-05-24 进一步明确：进入支持范围的厂商 API 必须具备 native 能力或可证明等价的 provider-specific native profile；类似资源属性必须具备可证明无损的转换翻译能力；不可对应、不可无损转换或目标厂商不能 native 执行的能力必须直接失败，不能通过 degraded、emulation、标记、header、metadata、local fake 或模拟返回让客户端误以为能力可用。Dify 这类非自有模型编排平台不属于模型 provider 支持范围，应删除或标为非目标；OpenAI、Anthropic、Gemini、MiMo、DeepSeek、xAI 等拥有自有模型/API 能力的头部厂商属于当前核心口径。

## 产品定义基线

- x-ai-gateway 是头部自研大模型厂商 API 网关，不是长尾聚合器、workflow 平台或通用 OpenAI-compatible 兼容器。
- 客户端可使用 OpenAI、Anthropic、Gemini、MiMo、DeepSeek、xAI 等受支持厂商的 native API 或 provider-specific native profile 接入。
- provider-specific native profile 只表示某个自有模型厂商以 OpenAI-style 或其它兼容协议暴露其官方能力，不等于接受任意 generic compatible 上游。
- 网关只在目标厂商具备 native 能力，且资源/属性可证明无损互转时返回成功。
- 不可对应、不可无损、非 native 能力必须直接失败，不能用 emulation、degraded、metadata/header、local fake 或模拟返回伪装成功。
- Dify 等非自有模型厂商是非目标；OpenRouter、Together、Fireworks、SiliconFlow 等聚合或中转平台不进入默认核心承诺；xAI 等有自有模型/API 能力的厂商保留。
- `/responses/compact` 只有 OpenAI Direct 或目标上游 native 等价能力可用时成功，否则明确返回 `unsupported` / `native_compaction_required`，不得称为 emulation。

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
- 不用 `local emulation`、`controlled translation`、`degraded`、metadata/header 标记或模拟返回来替代失败语义。
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
7. `/responses/compact` 等官方能力只有 OpenAI Direct 或目标上游提供 native 等价能力时才能成功；本地无法提供 native 等价能力时必须明确失败或返回 `unsupported` / `native_compaction_required`，不得称为 emulation。

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
- 2026-05-24：补充 native 直连现状审计。当前 OpenAI、Anthropic、Gemini 已有真实 runtime adapter；MiMo、DeepSeek、xAI、Qwen、Moonshot、Volcengine、MiniMax、Mistral、Perplexity 等仍主要是 provider-specific OpenAI-compatible contract，runtime 身份仍共用 `OPENAI_COMPATIBLE`；Cohere/Jina 已有 native contract 但 executor/smoke 证据未闭环。已拆分 `TASK-20260524-001-06`、`001-07`、`001-08` 进入 backlog，继续闭环 provider-specific runtime profile、embed/rerank native executor 和 degraded 能力层隔离。
- 2026-05-24：文档、SDK、coverage matrix 与 functional provider smoke 已进一步对齐。MiMo official smoke 使用 `XIAOMI_MIMO_OPENAI_COMPATIBLE` / `XIAOMI_MIMO_ANTHROPIC_COMPATIBLE` provider-specific protocol；Dify/OpenRouter/Together/Fireworks/SiliconFlow/generic 不进入默认 official smoke；`anthropic_compatible` / `openai_compatible` alias 只有在 MiMo baseUrl/profile 下才归一到 MiMo，不会把 DeepSeek 等其它自有厂商冒充为 MiMo native 证据。
- 2026-05-24：provider-specific runtime profile 首批落地。新增运行时 profile 描述，MiMo、DeepSeek、xAI 等具名厂商可在候选、路由亲和、interop plan debug、观测候选摘要和 metrics 中区分，不再只以 generic `OPENAI_COMPATIBLE` 暴露；MiMo conformance fixture 同步为 `XIAOMI_MIMO`。本轮不扩展数据库枚举，后续持久化迁移另行设计。
- 2026-05-24：文档口径同步已完成。新增 `docs/reports/REP-20260524-003-gateway-definition-sync.md`，并同步公开兼容、coverage matrix、media executor、provider smoke/pricing 与长尾 web search 文档，强调 OpenAI-style 只是入口协议，成功条件仍是 native/profile + lossless；不可对应能力必须硬失败。
- 2026-05-24：按最新产品口径再次同步 `docs/requirements/` 与 `docs/reports/` 中的网关定义表述。当前支持范围严格聚焦 OpenAI、Anthropic、Gemini、MiMo、DeepSeek、xAI 等头部自研模型/API 厂商；Dify 等非自有模型厂商作为非目标处理；`degraded`、`emulated`、metadata/header/local fake 和模拟返回不得作为成功语义；`/responses/compact` 等官方能力如无 native 等价能力必须明确失败或 unsupported。
- 2026-05-24：`TASK-20260524-001-08` 已完成并归档。`ALLOW_LOSSY` / `ALLOW_EMULATED` 不能作为执行成功条件；`BLOCKED` plan 会在凭证解析和 runtime/executor 调用前失败；默认 catalog 不再产出 `responses.emulated` 或旧 `emulate_with_chat_completions` 模式；错误规则旧降级默认语义改为阻断。
- 2026-05-24：`TASK-20260524-001-07` 已进入实施。Cohere/Jina 继续按 native embed/rerank provider 处理，当前切片要求 resource executor、capability truth 与 smoke/record-replay 同时区分 `PASS`、`FAIL`、`UNSUPPORTED`，不得用 generic OpenAI-compatible chat、Files/Uploads、metadata/header、degraded 或 local fake 作为成功证据。
- 2026-05-24：`TASK-20260524-001-06` 已完成并归档。MiMo、DeepSeek、xAI 已具备 provider-specific runtime profile、functional smoke/record-replay fixture、interop debug、observability 与非持久化 migration 证据；父目标当时剩余 4 个未 Done 子任务：`001-02`、`001-03`、`001-04`、`001-07`。
- 2026-05-24：`TASK-20260524-001-07` 最小 executor/smoke 闭环已落地但仍保持 In Progress。Cohere/Jina 已具备 resource-level native embed/rerank 执行路径与 hard-fail 边界；capability truth 不再把二者当作 chat/files/uploads provider；functional smoke / record-replay fixture 已能按 `COHERE_NATIVE`、`JINA_NATIVE` 区分 `PASS`、`FAIL`、`UNSUPPORTED`。当时剩余为真实 key live smoke 与 fixture 样本固化。
- 2026-05-24：`TASK-20260524-001-04` 已完成并归档。Responses compact/input_tokens/file_search、resource blocked plan、media native route required、Realtime current-down 文档和公开事实源已闭环；当时父目标剩余 3 个未 Done 子任务：`001-02`、`001-03`、`001-07`。
- 2026-05-24：`TASK-20260524-001-02` 已完成并归档。native adapter 最小契约、provider catalog 事实源、Admin/Public 透出、provider-specific OpenAI-compatible smoke 协议/path adapter、record/replay verifier 与 contract drift 验证已闭环；当时父目标剩余 2 个未 Done 子任务：`001-03` 无损翻译矩阵 mapper/smoke/docs 尾项、`001-07` Cohere/Jina live smoke 与 fixture 样本。
- 2026-05-24：`TASK-20260524-001-03` 已完成并归档。跨协议资源属性只允许 Lossless Translation Matrix 判定为 `LOSSLESS` 的路径成功；`NATIVE_REQUIRED` / `UNSUPPORTED` 会在 runtime、credential resolver 和 cooldown 前 hard-fail；mapper negative tests、smoke PASS/FAIL/UNSUPPORTED 分类、public docs/OpenAPI 与 conformance 验证已闭环。父目标当前剩余 1 个未 Done 子任务：`001-07` Cohere/Jina live smoke 与 fixture 样本。
- 2026-05-24：`TASK-20260524-001-07` record/replay 与结构证据切片继续推进。已固化 `COHERE_NATIVE`、`JINA_NATIVE` 独立 sample fixture，均覆盖 `PASS`、`FAIL`、`UNSUPPORTED`；verifier 明确禁止 Cohere/Jina 非 `EMBEDDINGS` / `RERANK` family 作为成功样本；smoke evidence 与 executor tests 已覆盖 Cohere `embeddings.float`、`meta.billed_units`、rerank `results[].relevance_score` 等官方结构证据。当前环境未发现 `COHERE` / `JINA` 相关环境变量名，真实 key live smoke 仍为唯一剩余前置条件，父目标保持 In Progress。
- 2026-05-24：`TASK-20260524-001-07` 已补真实 key live gate。新增 `FunctionalProviderSmokeLiveGateTests`，只有同时设置 `XAI_GATEWAY_FUNCTIONAL_PROVIDER_LIVE_SMOKE=true`、`XAI_GATEWAY_ALLOW_BILLABLE_SMOKE=true` 和 Cohere/Jina API key 时才访问真实上游；否则 JUnit assumption skipped，不产生成功证据。本轮无 key/gate 环境下验证结果为 `tests=2 skipped=2 failures=0 errors=0`，父目标仍保持 In Progress。
