package com.prodigalgal.xaigateway.gateway.core.shared;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ProtocolSuite {

    public static final String OPENAI_NATIVE = "openai.native";
    public static final String OPENAI_COMPATIBLE_GENERIC = "openai_compatible.generic";
    public static final String AZURE_OPENAI_COMPATIBLE = "azure_openai.openai_compatible";
    public static final String DEEPSEEK_OPENAI_COMPATIBLE = "deepseek.openai_compatible";
    public static final String XIAOMI_MIMO_OPENAI_COMPATIBLE = "xiaomi_mimo.openai_compatible";
    public static final String QWEN_OPENAI_COMPATIBLE = "qwen.openai_compatible";
    public static final String MOONSHOT_OPENAI_COMPATIBLE = "moonshot.openai_compatible";
    public static final String SILICONFLOW_OPENAI_COMPATIBLE = "siliconflow.openai_compatible";
    public static final String VOLCENGINE_OPENAI_COMPATIBLE = "volcengine.openai_compatible";
    public static final String MINIMAX_OPENAI_COMPATIBLE = "minimax.openai_compatible";
    public static final String DIFY_OPENAI_COMPATIBLE = "dify.openai_compatible";
    public static final String GROK_OPENAI_COMPATIBLE = "grok.openai_compatible";
    public static final String MISTRAL_OPENAI_COMPATIBLE = "mistral.openai_compatible";
    public static final String COHERE_OPENAI_COMPATIBLE = "cohere.openai_compatible";
    public static final String JINA_OPENAI_COMPATIBLE = "jina.openai_compatible";
    public static final String TOGETHER_OPENAI_COMPATIBLE = "together.openai_compatible";
    public static final String FIREWORKS_OPENAI_COMPATIBLE = "fireworks.openai_compatible";
    public static final String OPENROUTER_OPENAI_COMPATIBLE = "openrouter.openai_compatible";
    public static final String PERPLEXITY_OPENAI_COMPATIBLE = "perplexity.openai_compatible";
    public static final String ANTHROPIC_NATIVE = "anthropic.native";
    public static final String GEMINI_NATIVE = "gemini.native";
    public static final String VERTEX_AI_GEMINI_NATIVE = "vertex_ai.gemini_native";
    public static final String OLLAMA_NATIVE = "ollama.native";

    public static final List<String> OPENAI_COMPATIBLE_FAMILY = List.of(
            OPENAI_NATIVE,
            OPENAI_COMPATIBLE_GENERIC,
            AZURE_OPENAI_COMPATIBLE,
            DEEPSEEK_OPENAI_COMPATIBLE,
            XIAOMI_MIMO_OPENAI_COMPATIBLE,
            QWEN_OPENAI_COMPATIBLE,
            MOONSHOT_OPENAI_COMPATIBLE,
            SILICONFLOW_OPENAI_COMPATIBLE,
            VOLCENGINE_OPENAI_COMPATIBLE,
            MINIMAX_OPENAI_COMPATIBLE,
            DIFY_OPENAI_COMPATIBLE,
            GROK_OPENAI_COMPATIBLE,
            MISTRAL_OPENAI_COMPATIBLE,
            COHERE_OPENAI_COMPATIBLE,
            JINA_OPENAI_COMPATIBLE,
            TOGETHER_OPENAI_COMPATIBLE,
            FIREWORKS_OPENAI_COMPATIBLE,
            OPENROUTER_OPENAI_COMPATIBLE,
            PERPLEXITY_OPENAI_COMPATIBLE
    );

    private static final Map<UpstreamSiteKind, String> SITE_KIND_SUITES = Map.ofEntries(
            Map.entry(UpstreamSiteKind.OPENAI_DIRECT, OPENAI_NATIVE),
            Map.entry(UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC, OPENAI_COMPATIBLE_GENERIC),
            Map.entry(UpstreamSiteKind.AZURE_OPENAI, AZURE_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.DEEPSEEK, DEEPSEEK_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.QWEN, QWEN_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.MOONSHOT, MOONSHOT_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.SILICONFLOW, SILICONFLOW_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.VOLCENGINE, VOLCENGINE_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.MINIMAX, MINIMAX_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.DIFY, DIFY_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.GROK, GROK_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.MISTRAL, MISTRAL_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.COHERE, COHERE_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.JINA, JINA_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.TOGETHER, TOGETHER_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.FIREWORKS, FIREWORKS_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.OPENROUTER, OPENROUTER_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.PERPLEXITY, PERPLEXITY_OPENAI_COMPATIBLE),
            Map.entry(UpstreamSiteKind.ANTHROPIC_DIRECT, ANTHROPIC_NATIVE),
            Map.entry(UpstreamSiteKind.GEMINI_DIRECT, GEMINI_NATIVE),
            Map.entry(UpstreamSiteKind.VERTEX_AI, VERTEX_AI_GEMINI_NATIVE),
            Map.entry(UpstreamSiteKind.OLLAMA_DIRECT, OLLAMA_NATIVE)
    );

    private ProtocolSuite() {
    }

    public static String fromVendorAndSiteKind(String vendorCode, UpstreamSiteKind siteKind) {
        String vendor = normalize(vendorCode);
        if ("mimo".equals(vendor) || "xiaomi_mimo".equals(vendor)) {
            return XIAOMI_MIMO_OPENAI_COMPATIBLE;
        }
        if ("deepseek".equals(vendor)) {
            return DEEPSEEK_OPENAI_COMPATIBLE;
        }
        return fromSiteKind(siteKind);
    }

    public static String fromSiteKind(UpstreamSiteKind siteKind) {
        if (siteKind == null) {
            return OPENAI_COMPATIBLE_GENERIC;
        }
        return SITE_KIND_SUITES.getOrDefault(siteKind, OPENAI_COMPATIBLE_GENERIC);
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace('/', '.');
    }

    public static List<String> normalizeList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(ProtocolSuite::normalize)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
