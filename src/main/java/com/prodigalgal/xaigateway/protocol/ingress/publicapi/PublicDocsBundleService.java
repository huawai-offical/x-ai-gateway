package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import com.prodigalgal.xaigateway.admin.application.ProviderCatalogLoader;
import com.prodigalgal.xaigateway.admin.application.ProviderPresetDefinition;
import java.util.List;
import java.util.Locale;
import com.prodigalgal.xaigateway.gateway.core.cli.CloudCliClientDescriptor;
import com.prodigalgal.xaigateway.gateway.core.cli.CloudCliClientMatrixService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class PublicDocsBundleService {

    private final CloudCliClientMatrixService cloudCliClientMatrixService;
    private final ProviderCatalogLoader providerCatalogLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public PublicDocsBundleService(
            CloudCliClientMatrixService cloudCliClientMatrixService,
            ProviderCatalogLoader providerCatalogLoader) {
        this.cloudCliClientMatrixService = cloudCliClientMatrixService;
        this.providerCatalogLoader = providerCatalogLoader;
    }

    public PublicDocsBundleService() {
        this(new CloudCliClientMatrixService(), new ProviderCatalogLoader(new ObjectMapper()));
    }

    public PublicDocsBundleResponse bundle(String locale) {
        String normalized = normalizeLocale(locale);
        if ("en-US".equals(normalized)) {
            return english();
        }
        return chinese();
    }

    private PublicDocsBundleResponse chinese() {
        return new PublicDocsBundleResponse(
                "2026.05.06",
                "zh-CN",
                "x-ai-gateway 公开兼容文档",
                "/public/docs/openapi.json",
                "3.1.0",
                sdkTargets("zh-CN"),
                i18nPolicy("zh-CN"),
                List.of(
                        "创建或获取 Distributed Key。",
                        "把 OpenAI-compatible client 的 base URL 设置为 https://gateway.example.com/v1。",
                        "将 Authorization Bearer 设置为完整 Distributed Key secret。",
                        "先调用 /v1/chat/completions smoke，再按需启用 Claude、Gemini、Ollama 兼容路径。",
                        "失败时读取 error_code 和 requestId，再到管理端 trace 查询路由与账务记录。"
                ),
                compatibility(),
                providerPresets(),
                cliClients(),
                examples("zh-CN"),
                errorCodes("zh-CN"),
                List.of(
                        "路由优先遵守 Distributed Key 的 allowedModels、allowedProviderTypes、clientFamily 与 Route Policy runtime state。",
                        "Retry/Fallback 触发后，trace 中会保留 route decision、provider、credential 与 degradation level。",
                        "Realtime、Files、Batches、Caches 等非 Chat 能力需要先查看 provider capability matrix。"
                ),
                List.of(
                        "usage_record 记录 tokens 与 completeness，billing rollup 可按 day/week/month 聚合。",
                        "支付订单进入 PAID 后会写入 gateway_user_balance_ledger，重复 webhook 通过幂等键保护。",
                        "限流命中时优先检查 key 的 rpm/tpm/concurrency 和 runtime rate limit 状态。"
                ),
                List.of(
                        "chat.openai-compatible",
                        "claude.messages.translation",
                        "gemini.generate-content.translation",
                        "ollama.chat.native",
                        "files.cache.operations"
                )
        );
    }

    private PublicDocsBundleResponse english() {
        return new PublicDocsBundleResponse(
                "2026.05.06",
                "en-US",
                "x-ai-gateway Public Compatibility Docs",
                "/public/docs/openapi.json",
                "3.1.0",
                sdkTargets("en-US"),
                i18nPolicy("en-US"),
                List.of(
                        "Create or retrieve a Distributed Key.",
                        "Set your OpenAI-compatible base URL to https://gateway.example.com/v1.",
                        "Use the full Distributed Key secret as the Authorization Bearer token.",
                        "Run /v1/chat/completions first, then enable Claude, Gemini, or Ollama compatible flows.",
                        "On failures, inspect error_code and requestId, then query admin traces."
                ),
                compatibility(),
                providerPresets(),
                cliClients(),
                examples("en-US"),
                errorCodes("en-US"),
                List.of(
                        "Routing honors Distributed Key model/provider/client-family restrictions and Route Policy runtime state.",
                        "Retry/Fallback decisions are visible in traces with provider, credential and degradation details.",
                        "Realtime, Files, Batches and Caches depend on the provider capability matrix."
                ),
                List.of(
                        "usage_record stores token usage and completeness; billing rollup supports day/week/month windows.",
                        "A paid payment order appends gateway_user_balance_ledger entries with idempotent webhook protection.",
                        "For rate limit errors, check key rpm/tpm/concurrency and runtime rate limit state."
                ),
                List.of(
                        "chat.openai-compatible",
                        "claude.messages.translation",
                        "gemini.generate-content.translation",
                        "ollama.chat.native",
                        "files.cache.operations"
                )
        );
    }

    public JsonNode openApi() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("openapi", "3.1.0");
        ObjectNode info = root.putObject("info");
        info.put("title", "x-ai-gateway Public API");
        info.put("version", "2026.05.06");
        info.put("description", "公开接入面的最小 OpenAPI 事实源，覆盖 docs、OpenAI-compatible、Claude/Gemini 兼容入口和 Media provider matrix。");
        root.putArray("servers")
                .addObject()
                .put("url", "https://gateway.example.com")
                .put("description", "生产网关地址示例");
        ObjectNode paths = root.putObject("paths");
        addPath(paths, "get", "/public/docs/compatibility", "读取公开兼容文档 bundle", false);
        addPath(paths, "get", "/public/docs/openapi.json", "读取公开 OpenAPI JSON", false);
        addPath(paths, "post", "/v1/chat/completions", "OpenAI-compatible Chat Completions", true);
        addPath(paths, "post", "/v1/responses", "OpenAI-compatible Responses", true);
        addPath(paths, "post", "/v1/messages", "Claude Messages compatible endpoint", true);
        addPath(paths, "post", "/v1beta/models/{model}:generateContent", "Gemini generateContent compatible endpoint", true);
        addPath(paths, "post", "/api/v1/videos/generations", "创建 Video async task", true);
        addPath(paths, "get", "/api/v1/videos/{videoId}", "读取 Video async task", true);
        addPath(paths, "post", "/api/v1/videos/{videoId}/cancel", "取消 Video async task", true);
        addPath(paths, "get", "/api/v1/videos/{videoId}/download", "下载 Video async task 产物引用", true);
        addPath(paths, "post", "/api/v1/music/generations", "创建 Music async task", true);
        addPath(paths, "get", "/api/v1/music/{musicId}", "读取 Music async task", true);
        addPath(paths, "post", "/api/v1/music/{musicId}/cancel", "取消 Music async task", true);
        addPath(paths, "get", "/api/v1/music/{musicId}/download", "下载 Music async task 产物引用", true);
        addPath(paths, "get", "/api/v1/media/provider-matrix", "读取 Video/Music provider support matrix", true);
        ObjectNode components = root.putObject("components");
        components.putObject("securitySchemes")
                .putObject("bearerAuth")
                .put("type", "http")
                .put("scheme", "bearer")
                .put("bearerFormat", "Distributed Key");
        return root;
    }

    private List<PublicDocsCompatibilityResponse> compatibility() {
        return List.of(
                new PublicDocsCompatibilityResponse(
                        "openai",
                        "/v1",
                        List.of("OpenAI SDK", "Codex", "OpenCode", "OpenClaw", "curl"),
                        List.of("chat.completions", "responses", "embeddings", "files", "batches", "realtime"),
                        "OpenAI-compatible clients should use /v1 as base path."
                ),
                new PublicDocsCompatibilityResponse(
                        "claude",
                        "/v1",
                        List.of("Claude Code", "Anthropic SDK through compatible config"),
                        List.of("messages", "message_batches"),
                        "Claude native semantics are translated when provider capability allows it."
                ),
                new PublicDocsCompatibilityResponse(
                        "gemini",
                        "/v1",
                        List.of("Gemini CLI", "Google GenAI compatible clients"),
                        List.of("generateContent", "files", "cachedContents", "batches"),
                        "Gemini native objects are exposed through gateway resource lineage when needed."
                ),
                new PublicDocsCompatibilityResponse(
                        "ollama",
                        "/v1",
                        List.of("Ollama OpenAI-compatible clients"),
                        List.of("chat", "embeddings", "models"),
                        "Local Ollama deployments should be registered as provider sites before routing production traffic."
                ),
                new PublicDocsCompatibilityResponse(
                        "rerank",
                        "/v1",
                        List.of("Cohere-compatible clients", "Jina rerank clients", "curl"),
                        List.of("rerank"),
                        "Use dedicated rerank provider sites such as Cohere or Jina instead of general chat presets."
                )
        );
    }

    private List<PublicDocsProviderPresetResponse> providerPresets() {
        return providerCatalogLoader.load().presets().stream()
                .filter(preset -> !preset.deprecated())
                .map(this::toProviderPresetResponse)
                .toList();
    }

    private PublicDocsProviderPresetResponse toProviderPresetResponse(ProviderPresetDefinition preset) {
        return new PublicDocsProviderPresetResponse(
                preset.code(),
                preset.displayName(),
                preset.siteKind(),
                preset.compatibilitySurface(),
                preset.supportStrategy(),
                preset.capabilityTags(),
                preset.modelFamilies(),
                preset.pricingMetadata(),
                preset.unsupportedFeatures()
        );
    }

    private List<PublicDocsCliClientResponse> cliClients() {
        return cloudCliClientMatrixService.clients().stream()
                .map(this::toCliClientResponse)
                .toList();
    }

    private PublicDocsCliClientResponse toCliClientResponse(CloudCliClientDescriptor descriptor) {
        return new PublicDocsCliClientResponse(
                descriptor.client(),
                descriptor.clientFamily().name(),
                descriptor.protocol(),
                descriptor.basePath(),
                descriptor.requiredAuth(),
                descriptor.optionalMetadataHeaders(),
                descriptor.notes()
        );
    }

    private List<PublicDocsExampleResponse> examples(String locale) {
        boolean zh = "zh-CN".equals(locale);
        return List.of(
                new PublicDocsExampleResponse(
                        "curl",
                        "openai",
                        "shell",
                        zh ? "OpenAI-compatible Chat Smoke" : "OpenAI-compatible Chat Smoke",
                        """
                                curl https://gateway.example.com/v1/chat/completions \\
                                  -H "Authorization: Bearer $X_AI_GATEWAY_API_KEY" \\
                                  -H "Content-Type: application/json" \\
                                  -d '{"model":"gpt-4o-mini","messages":[{"role":"user","content":"ping"}]}'
                                """.trim()
                ),
                new PublicDocsExampleResponse(
                        "openai-sdk",
                        "openai",
                        "javascript",
                        zh ? "OpenAI SDK 接入" : "OpenAI SDK Setup",
                        """
                                import OpenAI from "openai";
                                const client = new OpenAI({
                                  apiKey: process.env.X_AI_GATEWAY_API_KEY,
                                  baseURL: "https://gateway.example.com/v1"
                                });
                                """.trim()
                ),
                new PublicDocsExampleResponse(
                        "claude-code",
                        "claude",
                        "shell",
                        zh ? "Claude Code 环境变量" : "Claude Code Environment",
                        """
                                export ANTHROPIC_API_KEY="$X_AI_GATEWAY_API_KEY"
                                export ANTHROPIC_BASE_URL="https://gateway.example.com/v1"
                                """.trim()
                ),
                new PublicDocsExampleResponse(
                        "gemini-cli",
                        "gemini",
                        "shell",
                        zh ? "Gemini CLI 环境变量" : "Gemini CLI Environment",
                        """
                                export GEMINI_API_KEY="$X_AI_GATEWAY_API_KEY"
                                export GEMINI_BASE_URL="https://gateway.example.com/v1"
                                """.trim()
                ),
                new PublicDocsExampleResponse(
                        "codex-cli",
                        "openai",
                        "shell",
                        zh ? "Codex CLI 云端代理接入" : "Codex CLI Cloud Gateway",
                        """
                                export OPENAI_API_KEY="$X_AI_GATEWAY_API_KEY"
                                export OPENAI_BASE_URL="https://gateway.example.com/v1"
                                """.trim()
                )
        );
    }

    private List<String> sdkTargets(String locale) {
        boolean zh = "zh-CN".equals(locale);
        return zh
                ? List.of("curl", "OpenAI SDK", "Claude Code", "Gemini CLI", "Codex CLI", "OpenCode", "Cursor")
                : List.of("curl", "OpenAI SDK", "Claude Code", "Gemini CLI", "Codex CLI", "OpenCode", "Cursor");
    }

    private List<String> i18nPolicy(String locale) {
        boolean zh = "zh-CN".equals(locale);
        return zh
                ? List.of(
                        "zh-CN 是管理端与 Portal 默认 UI 语言。",
                        "en-US 覆盖公开 docs bundle、OpenAPI 描述和 SDK 示例。",
                        "前端运行时语言切换尚未启用，后续先抽取导航、标题、表格列名、按钮和错误提示。"
                )
                : List.of(
                        "zh-CN is the default Admin and Portal UI language.",
                        "en-US covers the public docs bundle, OpenAPI descriptions and SDK examples.",
                        "Runtime frontend language switching is not enabled yet; navigation, titles, table headers, buttons and errors should be extracted first."
                );
    }

    private void addPath(ObjectNode paths, String method, String path, String summary, boolean bearerAuth) {
        ObjectNode pathNode = paths.putObject(path);
        ObjectNode operation = pathNode.putObject(method);
        operation.put("summary", summary);
        operation.put("operationId", operationId(method, path));
        operation.putArray("tags").add(path.startsWith("/public") ? "public-docs" : "gateway");
        if (bearerAuth) {
            operation.putArray("security").addObject().putArray("bearerAuth");
        }
        operation.putObject("responses")
                .putObject("200")
                .put("description", "OK");
    }

    private String operationId(String method, String path) {
        return (method + "_" + path)
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private List<PublicDocsErrorCodeResponse> errorCodes(String locale) {
        boolean zh = "zh-CN".equals(locale);
        return List.of(
                new PublicDocsErrorCodeResponse(
                        "invalid_api_key",
                        401,
                        zh ? "Distributed Key 无效、过期或未启用。" : "The Distributed Key is invalid, expired or disabled.",
                        zh ? "检查完整 secret、key 状态和过期时间。" : "Check the full secret, key status and expiration."
                ),
                new PublicDocsErrorCodeResponse(
                        "rate_limit_exceeded",
                        429,
                        zh ? "请求触发 key 或 route policy 限流。" : "The request hit key or route policy rate limits.",
                        zh ? "检查 rpm/tpm/concurrency 与 runtime rate state。" : "Check rpm/tpm/concurrency and runtime rate state."
                ),
                new PublicDocsErrorCodeResponse(
                        "no_route_available",
                        503,
                        zh ? "没有可用 provider、site、credential 或模型候选。" : "No provider, site, credential or model candidate is available.",
                        zh ? "检查 provider site、credential health、模型别名和能力矩阵。" : "Check provider sites, credential health, model aliases and capability matrix."
                ),
                new PublicDocsErrorCodeResponse(
                        "insufficient_balance",
                        402,
                        zh ? "用户余额或订阅额度不足。" : "The user balance or subscription quota is insufficient.",
                        zh ? "检查账务 rollup、订单状态和余额流水。" : "Check billing rollup, payment order status and balance ledger."
                )
        );
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "zh-CN";
        }
        String normalized = locale.trim().replace('_', '-');
        if ("en".equalsIgnoreCase(normalized) || "en-US".equalsIgnoreCase(normalized)) {
            return "en-US";
        }
        if ("zh".equalsIgnoreCase(normalized) || normalized.toLowerCase(Locale.ROOT).startsWith("zh-")) {
            return "zh-CN";
        }
        return "zh-CN";
    }
}
