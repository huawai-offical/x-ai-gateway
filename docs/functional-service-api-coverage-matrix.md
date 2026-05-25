# 功能性服务 API Coverage Matrix

状态：Derived Complete
日期：2026-05-23
关联任务：[TASK-20260514-029-01](../tasks/done/TASK-20260514-029-01-functional-service-api-coverage-matrix-source.md)、[TASK-20260514-029-04](../tasks/done/TASK-20260514-029-04-openapi-coverage-sdk-finalization.md)

## 事实源

机器可读源文件：

```text
src/main/resources/functional-service-api-coverage-matrix.json
```

该矩阵只描述 x-ai-gateway 当前产品范围：对话、streaming、tools/function calling、多模态输入输出，以及直接支撑这些能力的模型发现、RAG/file_search、认证、限流、审计、usage 和 smoke。它不是 OpenAI、Anthropic、Gemini、Vertex 或 Codex 官方 API 全量覆盖清单。

当前控制台中的 `能力矩阵` 页面已下线；这里保留的是 docs 内的事实源矩阵，用于约束公开 API、catalog 和测试口径，不表示仍存在对应前端主入口。

## 当前分类

| 分类 | 含义 | 示例 |
| --- | --- | --- |
| `core` | 对话和工具主链能力 | Chat Completions、Responses、Messages、GenerateContent、streaming、function tools |
| `supporting` | 为对话/tools 提供上下文、文件、检索或状态支撑 | Files、Uploads、Vector Stores、file_search、embeddings、Conversations、Webhooks、Realtime client secret 基线 |
| `governance` | gateway 自身运营治理能力 | model discovery、credential smoke、usage budget guard、audit |
| `out_of_scope` | 不进入公开兼容承诺 | Fine-tuning、Batches、Evals、Admin、provider-specific batch/job/pipeline、非 Responses Codex 内部 API |

## Provider 边界

- OpenAI：保留 Chat、Responses、streaming、function tools、多模态、Audio translations、Images edits/variations、Files/Uploads、Vector Stores/file_search、本地 Conversations、OpenAI Webhooks、Realtime client secret 基线、models 与治理支撑。
- Provider-specific OpenAI-compatible native profile：MiMo、DeepSeek、xAI、Qwen、Moonshot、Volcengine、MiniMax、Mistral 等仅在 catalog 声明 `nativeAdapterContract` 后进入默认核心能力矩阵；它们不是 generic fallback，不能把 OpenAI-style chat 兼容性自动外推为 Realtime、Batches、Files/Uploads 或完整 object lifecycle。audio/images/moderation/file/uploads 等资源面必须由 provider-specific native profile、capability snapshot 与 Lossless Translation Matrix 共同约束；不可无损或 native-only 的能力必须硬失败，不返回 warning/metadata/header/local emulation 伪成功。
- 非默认核心平台：Dify、OpenRouter、Together、Fireworks、SiliconFlow 与通用 generic compatible provider 默认不在核心支持范围；如未来保留，只能作为非核心可选 profile 单独声明，不进入本矩阵默认核心承诺。
- Anthropic：保留 Claude Messages、streaming、tool_use/thinking、图片输入理解与 file 支撑；不保留 audio 资源、图片生成/编辑/variation、Anthropic message batches、admin/eval 等 provider-specific 非核心 API。
- Gemini：保留 generateContent、streamGenerateContent、function calling、embeddings/files 等 Google native 支撑面；audio/image 等资源型入口只有在 Gemini native surface 或 Lossless Translation Matrix 明确允许时才执行，OpenAI surface 到 Gemini native 的 image edit、image variation、audio translation 已按 native-required 硬失败处理；不保留 batch prediction、tuning、pipeline/job/admin。
- Vertex：保留与 Gemini 对话和支撑面等价的 generateContent、embeddings/files 功能面；project/location 只是寻址和凭证边界，不扩展为 Vertex AI Platform 全量 API，audio/image 资源同样受 native surface 与 Lossless Translation Matrix 约束。
- Codex：只保留 ChatGPT 官方账号的 `/backend-api/codex/responses` smoke/proxy 边界，不注册为通用 provider catalog preset，不承诺非 Responses 内部 API。

## 后续派生

该矩阵已开始向以下文件收敛：

| 派生目标 | 状态 | 说明 |
| --- | --- | --- |
| `docs/openapi/public-openapi.json` | Done | `TASK-20260514-029-04` 已补全 core/supporting/governance 已实现路径，不声明 out_of_scope API。 |
| `src/main/resources/provider-catalog.json` | Done | 已按功能性服务 API 范围收紧 unsupportedFeatures 与 provider 边界。 |
| `docs/public-api-compatibility.md` | Done | 已明确 OpenAI Direct、provider-specific OpenAI-compatible native profile、Anthropic/Gemini/Vertex/Codex native 边界。 |
| `docs/public-sdk-examples.md` | Done | `TASK-20260514-029-04` 已补充 OpenAI Direct native、provider-specific OpenAI-compatible native profile、自定义 provider adapter 三模式示例。 |
| `src/test/resources/conformance/endpoint-conformance-matrix.json` | Done | 已按功能性服务 API 范围承接 endpoint conformance；Audio translations、Images edits/variations 已重新进入 OpenAI-style 资源入口，provider-specific file*/Uploads 由 native profile、capability snapshot 与 Lossless Translation Matrix 共同约束并在矩阵中独立验证。 |
| `src/test/resources/conformance/accepted-exceptions.json` | Done | 已将非核心 API 纳入 accepted exceptions 或 out-of-scope 决策。 |

本轮资源型接口继续推进后，provider-specific OpenAI-compatible native profile 的 audio/images/moderation 只有在 provider catalog、capability snapshot 与 Lossless Translation Matrix 均允许时才暴露；file*/Uploads 不再作为 accepted exception，是否可用取决于 capability snapshot 的 `supports_files` / `supports_uploads` 以及 provider catalog 是否明确支持该厂商对象生命周期。不可无损或 native-only 的跨协议资源属性必须转为硬失败，MiMo 等预设刷新后应写入最新 snapshot，避免旧 snapshot 把已实现入口误报为 blocked，也避免把未验证能力误报为成功。
