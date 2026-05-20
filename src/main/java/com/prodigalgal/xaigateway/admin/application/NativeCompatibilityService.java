package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.NativeCompatibilityResponse;
import com.prodigalgal.xaigateway.admin.api.NativeCompatibilityRoute;
import com.prodigalgal.xaigateway.admin.api.NativeTranslationConformanceRow;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NativeCompatibilityService {

    public NativeCompatibilityResponse matrix() {
        return new NativeCompatibilityResponse(List.of(
                new NativeCompatibilityRoute("ollama", "/ollama/api", "GET", "/ollama/api/tags", "SUPPORTED", true, "AUTH_GOVERNED", "列出当前 key 可访问模型，复用分发 Key 鉴权与模型目录。"),
                new NativeCompatibilityRoute("ollama", "/ollama/api", "POST", "/ollama/api/chat", "SUPPORTED", true, "AUTH_GOVERNED", "Ollama chat 请求转换为 canonical chat，响应转回 Ollama message 结构。"),
                new NativeCompatibilityRoute("anthropic", "/anthropic/v1", "POST", "/anthropic/v1/messages", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 `/v1/messages` 实现，仅保留 OpenAI 标准功能区内的 chat/tools/thinking。"),
                new NativeCompatibilityRoute("anthropic", "/anthropic/v1", "*", "/anthropic/v1/**", "EXPLICIT_UNSUPPORTED", true, "AUTH_GOVERNED", "非标准功能区路径返回兼容矩阵，不做未治理透明代理。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "POST", "/google/v1beta/models/{model}:generateContent", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Gemini generateContent，实现 OpenAI 标准功能区的 chat/tools 映射。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "POST", "/google/v1beta/models/{model}:embedContent", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Gemini embedding，实现 OpenAI 标准功能区的 embeddings 映射。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "POST", "/google/v1beta/models/{model}:batchEmbedContents", "ALIAS", true, "AUTH_GOVERNED", "同步批量 embeddings 仅作为 OpenAI embeddings 多输入等价面，不等同于 provider batch job。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "GET", "/google/v1beta/files", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Google native file list，实现本地目录治理。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "GET", "/google/v1beta/files/{fileName}", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Google native file get，并保持 lineage 校验。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "DELETE", "/google/v1beta/files/{fileName}", "ALIAS", true, "AUTH_GOVERNED", "命名空间别名复用现有 Google native file delete，并保持 lineage 校验。"),
                new NativeCompatibilityRoute("google", "/google/upload/v1beta", "POST", "/google/upload/v1beta/files", "ALIAS", true, "AUTH_GOVERNED", "Google upload namespace 复用现有 `/upload/v1beta/files` 文件上传实现。"),
                new NativeCompatibilityRoute("google", "/upload/v1beta", "POST", "/upload/v1beta/files", "SUPPORTED", true, "AUTH_GOVERNED", "通用 Google upload namespace 原生支持文件上传。"),
                new NativeCompatibilityRoute("google", "/v1beta", "*", "/v1beta/**", "SUPPORTED_GOVERNED", true, "AUTH_GOVERNED", "通用 Gemini `/v1beta` 仅支持 models/files 已建模标准区路径，未知路径显式拒绝。"),
                new NativeCompatibilityRoute("google", "/google/v1beta", "*", "/google/v1beta/**", "EXPLICIT_UNSUPPORTED", true, "AUTH_GOVERNED", "非标准功能区路径返回兼容矩阵，不做未治理透明代理。"),
                new NativeCompatibilityRoute("google", "/google/upload/v1beta", "*", "/google/upload/v1beta/**", "EXPLICIT_UNSUPPORTED", true, "AUTH_GOVERNED", "非显式支持 upload path 返回兼容矩阵，不做未治理透明代理。"),
                new NativeCompatibilityRoute("vertex", "provider-catalog:vertex", "*", "project/location generateContent + embeddings + files", "CATALOG_GOVERNED", true, "AUTH_GOVERNED", "Vertex 只按 Gemini 标准功能区建模；其它平台级能力不进入兼容面。"),
                new NativeCompatibilityRoute("codex", "chatgpt:/backend-api/codex", "POST", "/backend-api/codex/responses", "SMOKE_ONLY", true, "ADMIN_GOVERNED", "Codex App API 只按 OpenAI Responses 标准区做官方账号 smoke/反代校验，不扩展其它 Codex 内部接口。")
        ), translationConformance());
    }

    private List<NativeTranslationConformanceRow> translationConformance() {
        return List.of(
                new NativeTranslationConformanceRow(
                        "OpenAI",
                        "openai",
                        "/v1/chat/completions",
                        "native",
                        List.of("text", "vision", "tools", "tool_choice", "streaming", "usage", "finish_reason"),
                        List.of("provider-specific extra_body"),
                        List.of("unknown extension fields passthrough"),
                        "OpenAiChatCompletionsControllerTests + GatewayEndToEndSmokeTests",
                        "Chat Completions 已进入 canonical chat 主链路，OpenAI-compatible provider 需要按站点声明扩展参数。"
                ),
                new NativeTranslationConformanceRow(
                        "OpenAI",
                        "responses",
                        "/v1/responses",
                        "native",
                        List.of("input_text", "input_image", "input_file", "function_call_output", "tools", "reasoning"),
                        List.of("partial provider annotations"),
                        List.of("computer_use action replay"),
                        "OpenAiResponsesControllerTests + TranslationExplainServiceTests",
                        "Responses 主链路已建模，少量前沿 tool 类型需要继续以 lossy/unsupported 显式标记。"
                ),
                new NativeTranslationConformanceRow(
                        "Anthropic",
                        "anthropic",
                        "/v1/messages",
                        "lossy",
                        List.of("text", "image", "document", "tool_result", "system", "usage"),
                        List.of("streaming tool_use deltas", "thinking deltas", "tool schema passthrough"),
                        List.of("provider non-standard async APIs", "Anthropic admin/evals style APIs"),
                        "AnthropicMessagesControllerTests + AnthropicNativeGatewayChatRuntimeTests",
                        "Messages 可运行，但 streaming tool/reasoning 的事件级保真仍需硬化。"
                ),
                new NativeTranslationConformanceRow(
                        "Gemini",
                        "google",
                        "/v1beta/models/{model}:generateContent",
                        "lossy",
                        List.of("text", "fileData", "functionResponse", "functionDeclarations", "streaming", "usage"),
                        List.of("thinkingConfig", "toolChoice", "safety block normalization"),
                        List.of("provider non-standard async APIs", "Vertex pipeline/job/admin APIs"),
                        "GeminiGenerateContentControllerTests + GeminiNativeGatewayChatRuntimeTests",
                        "Gemini 主入口已支持，Vertex 与 thinking/toolChoice 差异需要后续补齐。"
                ),
                new NativeTranslationConformanceRow(
                        "OpenAI-compatible",
                        "openai-compatible",
                        "/v1/chat/completions",
                        "emulated",
                        List.of("text", "vision where provider supports it", "streaming", "basic usage"),
                        List.of("provider-specific params", "custom error code", "rate-limit headers"),
                        List.of("native non-OpenAI endpoints"),
                        "ProviderCatalogLoaderTests + SiteConformanceHarnessTests",
                        "OpenAI-compatible 以 catalog 和站点能力声明为事实源，不能自动等同原生 OpenAI。"
                ),
                new NativeTranslationConformanceRow(
                        "Azure OpenAI",
                        "azure-openai",
                        "/openai/deployments/{deployment}/chat/completions",
                        "emulated",
                        List.of("chat body via OpenAI shape", "deployment-to-model mapping"),
                        List.of("api-version behavior", "azure content filter details"),
                        List.of("deployment management APIs"),
                        "ProviderCatalogLoaderTests",
                        "需要用 deployment 与 api-version 维度继续完善真实 conformance。"
                ),
                new NativeTranslationConformanceRow(
                        "xAI / Perplexity",
                        "provider-specific",
                        "provider native endpoints",
                        "partial",
                        List.of("xAI OpenAI-compatible chat", "Perplexity search-augmented chat"),
                        List.of("provider-specific request/response extensions", "service-account smoke", "citation normalization"),
                        List.of("Bedrock/Baidu/Zhipu/Tencent native adapters", "provider-specific media lifecycle"),
                        "ProviderCatalogLoaderTests + SiteConformanceHarnessTests + ProviderReferenceGapServiceTests",
                        "xAI、Perplexity 已从笼统缺口收敛为 OpenAI-compatible 或 search-augmented Chat 兼容面；仍不能宣称所有长尾 provider 全自动无损翻译。"
                ),
                new NativeTranslationConformanceRow(
                        "Vertex AI",
                        "vertex",
                        "project/location Gemini standard zone",
                        "catalog-governed",
                        List.of("generateContent", "embeddings", "files support surface"),
                        List.of("Google Cloud credential and location routing"),
                        List.of("provider non-standard async APIs", "pipeline/job/admin APIs"),
                        "ProviderCatalogLoaderTests + ProviderReferenceGapServiceTests",
                        "Vertex 只按 OpenAI 标准功能区映射 Gemini 对话、tools、embeddings 和文件支撑；不追 Vertex AI Platform 全量 API。"
                ),
                new NativeTranslationConformanceRow(
                        "Codex App API",
                        "codex",
                        "/backend-api/codex/responses",
                        "smoke-only",
                        List.of("Responses request body", "streaming", "reasoning effort", "usage budget guard", "dry-run preview"),
                        List.of("ChatGPT account model entitlement", "record/replay fixture hardening"),
                        List.of("non-Responses Codex internal APIs", "admin/session/internal lifecycle APIs"),
                        "CodexResponsesSmokeHttpClientTests + OfficialAccountAdminServiceTests",
                        "Codex 只作为官方账号 Responses smoke/反代边界，不作为全量 provider catalog preset。"
                )
        );
    }
}
