package com.prodigalgal.xaigateway.gateway.core.shared;

import java.util.Locale;

public record ProviderRuntimeProfile(
        String key,
        String displayName,
        ProviderType providerType,
        UpstreamSiteKind siteKind,
        String protocolSuite,
        boolean providerSpecific
) {

    public static ProviderRuntimeProfile of(
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            String vendorCode,
            String baseUrl) {
        UpstreamSiteKind effectiveSiteKind = siteKind == null
                ? inferSiteKind(providerType, vendorCode, baseUrl)
                : siteKind;
        ProviderType effectiveProviderType = providerType == null
                ? providerTypeForSiteKind(effectiveSiteKind)
                : providerType;
        String key = keyFor(effectiveProviderType, effectiveSiteKind, vendorCode, baseUrl);
        return new ProviderRuntimeProfile(
                key,
                displayNameFor(key),
                effectiveProviderType,
                effectiveSiteKind,
                ProtocolSuite.fromVendorAndSiteKind(vendorCode, effectiveSiteKind),
                providerSpecificFor(effectiveSiteKind)
        );
    }

    public static String keyFor(
            ProviderType providerType,
            UpstreamSiteKind siteKind,
            String vendorCode,
            String baseUrl) {
        String vendor = normalizeVendor(vendorCode);
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        if (siteKind == UpstreamSiteKind.XIAOMI_MIMO
                || "xiaomi_mimo".equals(vendor)
                || "mimo".equals(vendor)
                || containsAny(normalizedBaseUrl, "xiaomimimo.com", "api.mimo-v2.com")) {
            return "XIAOMI_MIMO";
        }
        if (siteKind == UpstreamSiteKind.DEEPSEEK
                || "deepseek".equals(vendor)
                || containsAny(normalizedBaseUrl, "deepseek.com")) {
            return "DEEPSEEK";
        }
        if (siteKind == UpstreamSiteKind.GROK
                || "xai".equals(vendor)
                || "x_ai".equals(vendor)
                || "grok".equals(vendor)
                || containsAny(normalizedBaseUrl, "api.x.ai")) {
            return "XAI";
        }
        if (siteKind != null) {
            return switch (siteKind) {
                case OPENAI_DIRECT -> "OPENAI_DIRECT";
                case AZURE_OPENAI -> "AZURE_OPENAI";
                case OPENAI_COMPATIBLE_GENERIC -> "OPENAI_COMPATIBLE_GENERIC";
                case QWEN -> "QWEN";
                case MOONSHOT -> "MOONSHOT";
                case SILICONFLOW -> "SILICONFLOW";
                case VOLCENGINE -> "VOLCENGINE";
                case MINIMAX -> "MINIMAX";
                case DIFY -> "DIFY";
                case MISTRAL -> "MISTRAL";
                case COHERE -> "COHERE";
                case JINA -> "JINA";
                case TOGETHER -> "TOGETHER";
                case FIREWORKS -> "FIREWORKS";
                case OPENROUTER -> "OPENROUTER";
                case PERPLEXITY -> "PERPLEXITY";
                case ANTHROPIC_DIRECT -> "ANTHROPIC_DIRECT";
                case GEMINI_DIRECT -> "GEMINI_DIRECT";
                case VERTEX_AI -> "VERTEX_AI";
                case OLLAMA_DIRECT -> "OLLAMA_DIRECT";
                case XIAOMI_MIMO, DEEPSEEK, GROK -> siteKind.name();
            };
        }
        return providerType == null ? "UNKNOWN" : providerType.name();
    }

    public static UpstreamSiteKind inferSiteKind(ProviderType providerType, String vendorCode, String baseUrl) {
        if (providerType == null) {
            return null;
        }
        return switch (providerType) {
            case OPENAI_DIRECT -> UpstreamSiteKind.OPENAI_DIRECT;
            case ANTHROPIC_DIRECT -> UpstreamSiteKind.ANTHROPIC_DIRECT;
            case GEMINI_DIRECT -> UpstreamSiteKind.GEMINI_DIRECT;
            case OLLAMA_DIRECT -> UpstreamSiteKind.OLLAMA_DIRECT;
            case OPENAI_COMPATIBLE -> inferOpenAiCompatibleSiteKind(vendorCode, baseUrl);
        };
    }

    public static UpstreamSiteKind inferOpenAiCompatibleSiteKind(String vendorCode, String baseUrl) {
        String vendor = normalizeVendor(vendorCode);
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl);
        if ("xiaomi_mimo".equals(vendor)
                || "mimo".equals(vendor)
                || containsAny(normalizedBaseUrl, "xiaomimimo.com", "api.mimo-v2.com")) {
            return UpstreamSiteKind.XIAOMI_MIMO;
        }
        if ("deepseek".equals(vendor) || containsAny(normalizedBaseUrl, "deepseek.com")) {
            return UpstreamSiteKind.DEEPSEEK;
        }
        if ("xai".equals(vendor)
                || "x_ai".equals(vendor)
                || "grok".equals(vendor)
                || containsAny(normalizedBaseUrl, "api.x.ai")) {
            return UpstreamSiteKind.GROK;
        }
        if ("qwen".equals(vendor) || containsAny(normalizedBaseUrl, "dashscope.aliyuncs.com")) {
            return UpstreamSiteKind.QWEN;
        }
        if ("moonshot".equals(vendor) || containsAny(normalizedBaseUrl, "moonshot.cn")) {
            return UpstreamSiteKind.MOONSHOT;
        }
        if ("volcengine".equals(vendor) || containsAny(normalizedBaseUrl, "volces.com")) {
            return UpstreamSiteKind.VOLCENGINE;
        }
        if ("minimax".equals(vendor) || containsAny(normalizedBaseUrl, "minimax.chat")) {
            return UpstreamSiteKind.MINIMAX;
        }
        if ("mistral".equals(vendor) || containsAny(normalizedBaseUrl, "mistral.ai")) {
            return UpstreamSiteKind.MISTRAL;
        }
        if ("perplexity".equals(vendor) || containsAny(normalizedBaseUrl, "perplexity.ai")) {
            return UpstreamSiteKind.PERPLEXITY;
        }
        if ("cohere".equals(vendor) || containsAny(normalizedBaseUrl, "cohere.com")) {
            return UpstreamSiteKind.COHERE;
        }
        if ("jina".equals(vendor) || containsAny(normalizedBaseUrl, "jina.ai")) {
            return UpstreamSiteKind.JINA;
        }
        if ("dify".equals(vendor) || containsAny(normalizedBaseUrl, "dify.ai")) {
            return UpstreamSiteKind.DIFY;
        }
        if ("openrouter".equals(vendor) || containsAny(normalizedBaseUrl, "openrouter.ai")) {
            return UpstreamSiteKind.OPENROUTER;
        }
        if ("together".equals(vendor) || containsAny(normalizedBaseUrl, "together.xyz")) {
            return UpstreamSiteKind.TOGETHER;
        }
        if ("fireworks".equals(vendor) || containsAny(normalizedBaseUrl, "fireworks.ai")) {
            return UpstreamSiteKind.FIREWORKS;
        }
        if ("siliconflow".equals(vendor) || containsAny(normalizedBaseUrl, "siliconflow.cn")) {
            return UpstreamSiteKind.SILICONFLOW;
        }
        return UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC;
    }

    public static String normalizeVendor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static ProviderType providerTypeForSiteKind(UpstreamSiteKind siteKind) {
        if (siteKind == null) {
            return ProviderType.OPENAI_COMPATIBLE;
        }
        return switch (siteKind) {
            case OPENAI_DIRECT, AZURE_OPENAI -> ProviderType.OPENAI_DIRECT;
            case ANTHROPIC_DIRECT -> ProviderType.ANTHROPIC_DIRECT;
            case GEMINI_DIRECT, VERTEX_AI -> ProviderType.GEMINI_DIRECT;
            case OLLAMA_DIRECT -> ProviderType.OLLAMA_DIRECT;
            default -> ProviderType.OPENAI_COMPATIBLE;
        };
    }

    private static String displayNameFor(String key) {
        return switch (key) {
            case "OPENAI_DIRECT" -> "OpenAI";
            case "AZURE_OPENAI" -> "Azure OpenAI";
            case "XIAOMI_MIMO" -> "Xiaomi MiMo";
            case "DEEPSEEK" -> "DeepSeek";
            case "XAI" -> "xAI";
            case "QWEN" -> "Qwen";
            case "MOONSHOT" -> "Moonshot";
            case "VOLCENGINE" -> "Volcengine";
            case "MINIMAX" -> "MiniMax";
            case "MISTRAL" -> "Mistral";
            case "COHERE" -> "Cohere";
            case "JINA" -> "Jina";
            case "PERPLEXITY" -> "Perplexity";
            case "ANTHROPIC_DIRECT" -> "Anthropic";
            case "GEMINI_DIRECT" -> "Gemini";
            case "VERTEX_AI" -> "Vertex AI";
            case "OLLAMA_DIRECT" -> "Ollama";
            default -> key;
        };
    }

    private static boolean providerSpecificFor(UpstreamSiteKind siteKind) {
        return siteKind != null
                && siteKind != UpstreamSiteKind.OPENAI_DIRECT
                && siteKind != UpstreamSiteKind.OPENAI_COMPATIBLE_GENERIC
                && siteKind != UpstreamSiteKind.ANTHROPIC_DIRECT
                && siteKind != UpstreamSiteKind.GEMINI_DIRECT
                && siteKind != UpstreamSiteKind.OLLAMA_DIRECT;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        return baseUrl == null ? "" : baseUrl.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
