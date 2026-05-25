# REP-20260524-003 网关定义口径同步

日期：2026-05-24

## 结论

x-ai-gateway 当前公开定义收口为：它是头部自研大模型厂商 API 网关，不是长尾聚合器、workflow 平台或通用 OpenAI-compatible 兼容器。客户端可用 OpenAI、Anthropic、Gemini、MiMo、DeepSeek、xAI 等受支持厂商的 native API 或 provider-specific native profile 接入网关；网关只在目标厂商具备 native 能力、且资源/属性可以确定性无损互转时返回成功。不可对应、不可无损、不可 native 的能力必须直接失败，不使用 local emulation、degraded marker、header、metadata、warning 或 local fake 伪装成功。

## 默认核心范围

默认核心 provider 以 OpenAI、Anthropic、Gemini/Vertex、MiMo、DeepSeek、xAI，以及其它拥有自有模型、稳定 API、明确进入支持清单的厂商为主。Qwen、Moonshot、Volcengine、MiniMax、Mistral、Perplexity、Cohere、Jina 等只有在 catalog、native/profile contract、capability snapshot、smoke 证据和 Lossless Translation Matrix 共同允许时进入已支持或部分支持。

Dify、OpenRouter、Together、Fireworks、SiliconFlow 与通用 generic compatible provider 不再是默认核心支持范围；如未来保留，只能作为非核心 profile、长尾 adapter 或 workflow-compatible 场景单独声明。

## Compact 口径

`/responses/compact` 只有 OpenAI Direct 或目标上游具备 native 等价 compaction 能力时才允许返回成功。其它路径必须明确失败，错误语义使用 `unsupported` / `native_compaction_required`；历史文档中的 local opaque marker、emulation fallback 或 degraded 成功不再代表当前产品定义。

## 本轮限定范围

本轮任务 A 只同步 `docs/requirements/` 与 `docs/reports/` 下相关 Markdown。公开 docs 根目录文档、`docs/decisions/`、`tasks/`、代码和测试不在本轮写入范围内。

## 同步结果

- `docs/public-api-compatibility.md`：改为 native/lossless 边界说明，明确 OpenAI-style 只是协议入口，不等于 generic OpenAI-compatible 成功承诺。
- `docs/functional-service-api-coverage-matrix.md`：把 OpenAI-compatible generic 改为 provider-specific native profile，并把资源型能力收口到 native profile、capability snapshot 与 Lossless Translation Matrix。
- `docs/public-sdk-examples.md`：把 SDK 示例中的 generic 模式改为 provider-specific OpenAI-compatible native profile，并强调 hard-fail。
- `docs/testing-smoke-harness.md`：把 Gemini/MiMo smoke 改为核心 provider native / provider-specific 证据，不允许 Dify、OpenRouter、Together、Fireworks、SiliconFlow、generic 进入默认 official smoke。
- `docs/reports/REP-20260524-001-native-adapter-minimum-contract.md`：补充 runtime profile 缺口、smoke fixture 口径和旧 degraded/emulated 层隔离要求。
- `docs/reports/REP-20260524-002-lossless-translation-matrix.md`：明确 provider-specific OpenAI-style 同协议执行不等于 generic fallback 成功。
- `docs/requirements/REQ-20260524-001-head-provider-native-lossless-gateway-scope.md`：补充产品定义基线，明确 x-ai-gateway 不是长尾聚合器/workflow 平台兼容器，并固定 `/responses/compact` 的 native-only 成功条件。
- `docs/reports/REP-20260524-003-gateway-definition-sync.md`：补充本轮限定范围、compact 口径和历史记录风险边界。
- `src/main/resources/provider-catalog.json` / `ProviderCatalogLoader`：默认事实源不再产出 `responses.emulated` 或 `emulate_with_chat_completions`，改为 `responses.native` / `responses.lossless-translation` / `lossless_to_chat_completions`；运行时不再接受旧 `emulate_with_chat_completions` 模式作为成功开关。
- `docs/media-provider-executors.md`、`docs/provider-smoke-pricing-sync.md`、`docs/provider-long-tail-web-search-native-adapter.md`：保留历史实现记录，但增加 2026-05-24 口径提示，避免读成当前默认核心承诺。

## 剩余不确定项

以下文档仍包含历史时期的 OpenAI-compatible、长尾 provider、emulated/degraded 或 parity 语句，但多数属于需求、迁移、历史对标报告或已完成任务记录。本轮不大范围改写历史文档，只在现行公开文档和容易误读的入口文档中同步口径：

- `docs/reports/REP-20260506-*`、`docs/reports/REP-20260513-*`、`docs/reports/REP-20260514-*`：保留历史对标和旧 backlog 记录；其中关于 Dify、OpenRouter、Together、Fireworks、SiliconFlow、generic compatible、emulation 或 degraded 成功的描述不能作为当前默认核心承诺。
- `docs/requirements/REQ-20260522-*`、`docs/requirements/REQ-20260523-*`：保留当时任务背景；其中 `OpenAI-compatible generic`、`emulated/translated` 或 gateway-local orchestration 语句需要按 `REQ-20260524-001` 的 native/profile + lossless/hard-fail 口径解释。
- `docs/realtime-provider-websocket.md`、`docs/openai-conversations-local-lifecycle.md`、`docs/client-onboarding-pack.md`：仍有 OpenAI-compatible 术语，需后续结合 runtime provider profile 改造结果判断是否改为 OpenAI-style / gateway-local。

## 验证关键词

本轮验证使用 `rg` 搜索以下关键词：

```text
REQ-20260524-001|REP-20260524|emulation|emulated|degraded|degradation|Dify|OpenRouter|Together|Fireworks|SiliconFlow|generic|OpenAI-compatible|provider|厂商|OpenAI|Anthropic|Gemini|MiMo|DeepSeek|xAI|Responses|compact|lossless|native
```
