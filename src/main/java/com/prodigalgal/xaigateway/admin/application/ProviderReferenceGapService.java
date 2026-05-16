package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.admin.api.ProviderMediaCapabilityRow;
import com.prodigalgal.xaigateway.admin.api.ProviderPricingSyncStatusRow;
import com.prodigalgal.xaigateway.admin.api.ProviderReferenceGapResponse;
import com.prodigalgal.xaigateway.admin.api.ProviderReferenceGapRow;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProviderReferenceGapService {

    private static final Instant MATRIX_GENERATED_AT = Instant.parse("2026-05-13T00:00:00Z");
    private static final Instant LAST_VERIFIED_AT = Instant.parse("2026-05-14T00:00:00Z");
    private static final List<String> STANDARD_FAILURE_CLASSES = List.of(
            "AUTHENTICATION_FAILED",
            "QUOTA_EXCEEDED",
            "NETWORK_ERROR",
            "PARAMETER_UNSUPPORTED",
            "PROVIDER_RATE_LIMITED"
    );

    private final ProviderCatalogLoader providerCatalogLoader;
    private final ProviderPricingSnapshotService pricingSnapshotService;

    @Autowired
    public ProviderReferenceGapService(
            ProviderCatalogLoader providerCatalogLoader,
            ProviderPricingSnapshotService pricingSnapshotService) {
        this.providerCatalogLoader = providerCatalogLoader;
        this.pricingSnapshotService = pricingSnapshotService;
    }

    public ProviderReferenceGapService(ProviderCatalogLoader providerCatalogLoader) {
        this(providerCatalogLoader, new ProviderPricingSnapshotService());
    }

    public ProviderReferenceGapResponse get() {
        ProviderCatalogSnapshot snapshot = providerCatalogLoader.load();
        Map<String, ProviderPresetDefinition> catalog = snapshot.presets().stream()
                .collect(Collectors.toMap(
                        ProviderPresetDefinition::code,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        return new ProviderReferenceGapResponse(
                "new-api relay/channel",
                "2026-05-reference-scan",
                snapshot.version(),
                snapshot.source(),
                MATRIX_GENERATED_AT,
                providerRows(catalog),
                mediaRows(catalog),
                pricingRows(catalog, pricingSnapshots(snapshot)),
                List.of(
                        "优先把 MISSING 中具备稳定 OpenAI-compatible surface 的 provider 收敛到 Provider Catalog preset，而不是先写透明代理。",
                        "价格同步先以 provider 官方公开价格或 operator-configured pricing 标记来源，再接真实同步 job。",
                        "真实 smoke 继续使用环境变量注入测试 key，并把失败归类为认证、额度、网络、参数不支持或 provider 限流。"
                )
        );
    }

    private List<ProviderReferenceGapRow> providerRows(Map<String, ProviderPresetDefinition> catalog) {
        return referenceChannels().stream()
                .map(reference -> toProviderRow(reference, catalog.get(reference.catalogPresetCode())))
                .toList();
    }

    private ProviderReferenceGapRow toProviderRow(ReferenceChannel reference, ProviderPresetDefinition preset) {
        String status = preset == null && reference.catalogPresetCode() != null ? "MISSING" : reference.supportStatus();
        List<String> missingFeatures = preset == null ? reference.missingFeatures() : preset.unsupportedFeatures();
        return new ProviderReferenceGapRow(
                reference.channel(),
                reference.catalogPresetCode(),
                reference.displayName(),
                status,
                reference.supportMode(),
                preset == null ? reference.currentSurface() : preset.compatibilitySurface(),
                reference.adapterBoundary(),
                preset == null ? List.of() : preset.capabilityTags(),
                missingFeatures,
                reference.notes()
        );
    }

    private List<ProviderMediaCapabilityRow> mediaRows(Map<String, ProviderPresetDefinition> catalog) {
        return List.of(
                media("audio", "/v1/audio/*", "SUPPORTED_GOVERNED",
                        presetsWithTags(catalog, Set.of("audio"), List.of("gemini")),
                        "OpenAI passthrough 与 Gemini native executor；非 OpenAI-compatible provider 需要显式能力声明。",
                        "mock executor + 可选 Gemini/OpenAI 真实 key smoke"),
                media("image", "/v1/images/*", "SUPPORTED_GOVERNED",
                        presetsWithTags(catalog, Set.of("images"), List.of("gemini")),
                        "OpenAI image endpoint 与 Gemini image generation 已建模，edit/variation 按能力矩阵显式标记。",
                        "mock executor + 可选图片生成真实 key smoke"),
                media("video", "/api/v1/videos/*", "PROVIDER_ADAPTER",
                        presentCodes(catalog, List.of("gemini")),
                        "当前以 Gemini Veo provider-specific adapter 和 gateway async resource lifecycle 承载。",
                        "本地生命周期 smoke；真实 Veo key 从环境变量注入"),
                media("music", "/api/v1/music/*", "PROVIDER_ADAPTER",
                        List.of("operator-configured", "suno-like"),
                        "Suno-like Music 已由 provider-specific adapter 承载；MiniMax/Udio 等专有音乐 provider 仍需按目标继续拆分。",
                        "Suno-like 本地生命周期 smoke；真实 smoke 需 XAG_SMOKE_SUNO=true 与测试 key，缺 key 时 SKIPPED"),
                media("realtime", "/v1/realtime/client_secrets", "PARTIAL",
                        presetsWithTags(catalog, Set.of("realtime"), List.of()),
                        "OpenAI client_secret 语义可治理；Gemini live token 与 OpenAI realtime 不等价，必须显式阻断。",
                        "OpenAI realtime mock + 可选真实 key smoke"),
                media("rerank", "/v1/rerank", "SUPPORTED_GOVERNED",
                        presetsWithTags(catalog, Set.of("rerank"), List.of()),
                        "Cohere/Jina 按 dedicated rerank provider 建模，不把所有 chat provider 自动视为 rerank。",
                        "conformance fixture + 可选 Jina/Cohere 真实 key smoke"),
                media("web_search", "/v1/web_search", "PROVIDER_ADAPTER",
                        presentCodes(catalog, List.of("openai", "perplexity")),
                        "OpenAI 原生 web_search 与 Perplexity search-augmented chat 分开治理；不把所有 OpenAI-compatible provider 自动视为 web_search provider。",
                        "conformance fixture + 可选 OpenAI/Perplexity 真实 key smoke，缺 key 时 SKIPPED")
        );
    }

    private ProviderMediaCapabilityRow media(
            String capability,
            String endpointSurface,
            String supportStatus,
            List<String> providerPresets,
            String governanceBoundary,
            String smokeHint) {
        return new ProviderMediaCapabilityRow(
                capability,
                endpointSurface,
                supportStatus,
                providerPresets,
                governanceBoundary,
                smokeHint
        );
    }

    private Map<String, ProviderPricingSnapshotView> pricingSnapshots(ProviderCatalogSnapshot snapshot) {
        return pricingSnapshotService.fromCatalog(snapshot, LAST_VERIFIED_AT).stream()
                .collect(Collectors.toMap(
                        ProviderPricingSnapshotView::providerCode,
                        item -> item,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<ProviderPricingSyncStatusRow> pricingRows(
            Map<String, ProviderPresetDefinition> catalog,
            Map<String, ProviderPricingSnapshotView> pricingSnapshots) {
        return List.of(
                pricing(catalog, pricingSnapshots, "openai", "OpenAI 官方公开价格", "public-list-price checksum + manual review", "PUBLIC_SOURCE_TRACKED", false,
                        "mock-smoke + optional-real-key", "公开价格可同步，真实调用 smoke 需要测试 key。"),
                pricing(catalog, pricingSnapshots, "anthropic", "Anthropic 官方公开价格", "public-list-price checksum + manual review", "PUBLIC_SOURCE_TRACKED", true,
                        "mock-smoke + optional-real-key", "Messages 与 batch 价格需按模型族独立校准。"),
                pricing(catalog, pricingSnapshots, "gemini", "Google AI Studio / Gemini API 公开价格", "public-list-price checksum + AI Studio smoke", "PUBLIC_SOURCE_TRACKED", true,
                        "mock-smoke + optional-real-key", "免费 AI Studio key 可用于真实 smoke，但价格仍以公开价格源为准。"),
                pricing(catalog, pricingSnapshots, "deepseek", "Provider console price table", "operator-reviewed provider console snapshot", "OPERATOR_REVIEW_REQUIRED", true,
                        "mock-smoke + optional-real-key", "需要记录快照时间，避免 provider-console 价格漂移。"),
                pricing(catalog, pricingSnapshots, "qwen", "DashScope price table", "operator-reviewed provider console snapshot", "OPERATOR_REVIEW_REQUIRED", true,
                        "mock-smoke + optional-real-key", "DashScope compatible mode 与 native 模型价格需要拆开。"),
                pricing(catalog, pricingSnapshots, "openrouter", "OpenRouter models/pricing metadata", "aggregator pass-through metadata", "AGGREGATOR_METADATA", true,
                        "mock-smoke + optional-real-key", "聚合站价格由上游模型决定，不能固化为单一 provider 单价。"),
                pricing(catalog, pricingSnapshots, "cohere", "Cohere 官方公开价格", "public-list-price checksum + rerank smoke", "PUBLIC_SOURCE_TRACKED", true,
                        "mock-smoke + optional-real-key", "rerank 与 embeddings 需分 surface 计价。"),
                pricing(catalog, pricingSnapshots, "jina", "Jina provider console price table", "operator-reviewed provider console snapshot", "OPERATOR_REVIEW_REQUIRED", true,
                        "mock-smoke + optional-real-key", "Jina 当前聚焦 embeddings/rerank，不作为通用 chat 计价。"),
                pricing(catalog, pricingSnapshots, "xai", "xAI 官方公开价格", "public-list-price checksum + manual review", "PUBLIC_SOURCE_TRACKED", true,
                        "mock-smoke + optional-real-key", "xAI chat/responses 与专有 media/tool 能力需分 surface 计价。"),
                pricing(catalog, pricingSnapshots, "perplexity", "Perplexity 官方公开价格", "public-list-price checksum + manual review", "PUBLIC_SOURCE_TRACKED", true,
                        "mock-smoke + optional-real-key", "Perplexity 按搜索增强模型族计价，不能并入通用 OpenAI-compatible 单价。"),
                pricing(catalog, pricingSnapshots, "vertex", "Vertex AI 官方公开价格", "public-list-price checksum + service-account smoke", "PUBLIC_SOURCE_TRACKED", true,
                        "mock-smoke + optional-real-key", "Vertex 价格按 project/location 和 Google Cloud 模型族治理，不能复用 AI Studio key。")
        );
    }

    private ProviderPricingSyncStatusRow pricing(
            Map<String, ProviderPresetDefinition> catalog,
            Map<String, ProviderPricingSnapshotView> pricingSnapshots,
            String providerCode,
            String pricingSource,
            String syncStrategy,
            String syncStatus,
            boolean requiresRealKey,
            String smokeClassification,
            String notes) {
        ProviderPresetDefinition preset = catalog.get(providerCode);
        ProviderPricingSnapshotView snapshot = pricingSnapshots.get(providerCode);
        return new ProviderPricingSyncStatusRow(
                providerCode,
                preset == null ? providerCode : preset.displayName(),
                snapshot == null ? pricingSource : snapshot.sourceRef(),
                syncStrategy,
                snapshot == null ? syncStatus : snapshot.syncStatus(),
                snapshot == null ? LAST_VERIFIED_AT : snapshot.lastVerifiedAt(),
                snapshot == null ? "" : snapshot.snapshotVersion(),
                snapshot == null ? "" : snapshot.checksum(),
                snapshot == null ? "UNKNOWN" : snapshot.sourceKind(),
                snapshot == null ? "UNKNOWN" : snapshot.approvalStatus(),
                snapshot == null ? null : snapshot.effectiveAt(),
                snapshot == null ? null : snapshot.supersededAt(),
                snapshot == null ? "UNKNOWN" : snapshot.driftStatus(),
                snapshot != null && snapshot.productionEligible(),
                smokeClassification,
                STANDARD_FAILURE_CLASSES,
                requiresRealKey,
                snapshot == null ? notes : notes + " " + snapshot.notes()
        );
    }

    private List<String> presetsWithTags(
            Map<String, ProviderPresetDefinition> catalog,
            Set<String> tags,
            List<String> extraCodes) {
        Set<String> normalizedTags = tags.stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        catalog.values().stream()
                .filter(preset -> preset.capabilityTags().stream()
                        .map(item -> item.toLowerCase(Locale.ROOT))
                        .anyMatch(normalizedTags::contains))
                .forEach(preset -> values.put(preset.code(), preset.code()));
        presentCodes(catalog, extraCodes).forEach(code -> values.put(code, code));
        return List.copyOf(values.keySet());
    }

    private List<String> presentCodes(Map<String, ProviderPresetDefinition> catalog, List<String> codes) {
        return codes.stream()
                .filter(catalog::containsKey)
                .toList();
    }

    private List<ReferenceChannel> referenceChannels() {
        return List.of(
                supported("openai", "openai", "OpenAI", "native-first", "OpenAI native 与 compatible 主入口已覆盖。"),
                supported("claude", "anthropic", "Anthropic Claude", "translation-layer", "以 Anthropic Messages/native namespace 承载 new-api 的 claude channel。"),
                supported("gemini", "gemini", "Google Gemini", "translation-layer", "Gemini generateContent/files/batches 已建模，Vertex 独立缺口另列。"),
                supported("deepseek", "deepseek", "DeepSeek", "openai-compatible", "OpenAI-compatible chat/reasoning 走 catalog preset。"),
                supported("ali", "qwen", "Qwen / Ali DashScope", "openai-compatible", "new-api ali channel 对应本项目 qwen preset。"),
                supported("moonshot", "moonshot", "Moonshot / Kimi", "openai-compatible", "OpenAI-compatible chat 已进入 catalog。"),
                supported("siliconflow", "siliconflow", "SiliconFlow", "openai-compatible", "聚合站模式按 provider-compatible pricing 管理。"),
                supported("volcengine", "volcengine", "Volcengine Ark", "path-adapter-required", "Ark /api/v3 path adapter 已在 preset 中显式标注。"),
                supported("minimax", "minimax", "MiniMax", "openai-compatible", "OpenAI-compatible chat 已进入 catalog。"),
                supported("dify", "dify", "Dify", "workflow-openai-compatible", "Dify 仅作为 workflow/chat compatible，不等价全量 OpenAI lifecycle。"),
                supported("openrouter", "openrouter", "OpenRouter", "aggregator-routing", "聚合站按 pass-through pricing 与路由边界治理。"),
                supported("cohere", "cohere", "Cohere", "native-rerank", "Cohere 兼容 chat/embeddings，同时作为 rerank provider。"),
                supported("jina", "jina", "Jina", "native-rerank", "Jina 聚焦 embeddings/rerank，不作为通用 chat provider。"),
                supported("mistral", "mistral", "Mistral", "path-adapter-required", "Mistral compatible path 需要继续按官方差异硬化。"),
                compatible("codex", "Codex account proxy", "account-proxy", "Codex 不是通用 provider catalog preset，而是官方账号/Responses 反代面。"),
                compatible("ollama", "Ollama", "manual-site-kind", "已有 Ollama native namespace 和 site kind，但缺少 catalog preset 导入体验。"),
                supported("xai", "xai", "xAI / Grok", "openai-compatible", "GROK site kind 与 catalog preset 已收敛，Responses/web search 等专有能力继续按 adapter 边界声明。"),
                supported("perplexity", "perplexity", "Perplexity", "web-search-openai-compatible", "作为 search-augmented Chat Completions provider 单独建模，web_search 不泛化给所有 compatible 站点。"),
                supported("vertex", "vertex", "Vertex AI", "vertex-google-native", "以 Google Cloud project/location native path 建模，凭证与 AI Studio Gemini 分开治理。"),
                missing("aws", "AWS Bedrock", List.of("sigv4 auth", "model path adapter", "pricing source"), "需要独立鉴权和模型路径 adapter。"),
                missing("baidu", "Baidu Wenxin", List.of("catalog preset", "auth adapter", "pricing source"), "参考项目 channel 未在当前 catalog 中覆盖。"),
                missing("zhipu", "Zhipu", List.of("catalog preset", "pricing source", "smoke"), "参考项目 channel 未在当前 catalog 中覆盖。"),
                missing("tencent", "Tencent Hunyuan", List.of("catalog preset", "auth adapter", "pricing source"), "参考项目 channel 未在当前 catalog 中覆盖。"),
                missing("xunfei", "iFlytek Spark", List.of("catalog preset", "auth adapter", "pricing source"), "参考项目 channel 未在当前 catalog 中覆盖。"),
                missing("ai360", "360 AI", List.of("catalog preset", "pricing source"), "参考项目 channel 未在当前 catalog 中覆盖。"),
                missing("cloudflare", "Cloudflare Workers AI", List.of("catalog preset", "account scoped auth", "pricing source"), "参考项目 channel 未在当前 catalog 中覆盖。"),
                missing("coze", "Coze", List.of("workflow adapter", "pricing source"), "与 Dify 类似，不能直接视作通用 OpenAI-compatible provider。"),
                missing("jimeng", "Jimeng", List.of("media adapter", "pricing source", "smoke"), "偏媒体生成，需要 provider-specific media adapter。"),
                missing("lingyiwanwu", "Lingyiwanwu", List.of("catalog preset", "pricing source"), "参考项目 channel 未在当前 catalog 中覆盖。"),
                missing("replicate", "Replicate", List.of("async adapter", "pricing source", "media lifecycle"), "需要 async task 与模型级 pricing。"),
                missing("xinference", "Xinference", List.of("local deployment preset", "health discovery"), "需要本地部署发现与模型同步策略。")
        );
    }

    private ReferenceChannel supported(String channel, String catalogCode, String displayName, String supportMode, String notes) {
        return new ReferenceChannel(channel, catalogCode, displayName, "SUPPORTED", supportMode, "", "catalog + capability matrix", List.of(), notes);
    }

    private ReferenceChannel compatible(String channel, String displayName, String supportMode, String notes) {
        return new ReferenceChannel(channel, null, displayName, "COMPATIBLE", supportMode, "account/runtime namespace", "not a provider catalog preset", List.of(), notes);
    }

    private ReferenceChannel missing(String channel, String displayName, List<String> missingFeatures, String notes) {
        return new ReferenceChannel(channel, null, displayName, "MISSING", "not-implemented", "", "requires dedicated preset/adapter", missingFeatures, notes);
    }

    private record ReferenceChannel(
            String channel,
            String catalogPresetCode,
            String displayName,
            String supportStatus,
            String supportMode,
            String currentSurface,
            String adapterBoundary,
            List<String> missingFeatures,
            String notes
    ) {
    }
}
