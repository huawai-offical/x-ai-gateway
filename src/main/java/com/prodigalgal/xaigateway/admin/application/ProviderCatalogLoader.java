package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProviderCatalogLoader {

    private static final String CATALOG_RESOURCE = "provider-catalog.json";

    private final ObjectMapper objectMapper;
    private final Path marketplaceCurrentPath;

    @Autowired
    public ProviderCatalogLoader(ObjectMapper objectMapper, GatewayProperties gatewayProperties) {
        this.objectMapper = objectMapper;
        this.marketplaceCurrentPath = marketplaceRoot(gatewayProperties).resolve("current.json");
    }

    public ProviderCatalogLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.marketplaceCurrentPath = null;
    }

    ObjectMapper objectMapper() {
        return objectMapper;
    }

    public ProviderCatalogSnapshot load() {
        Optional<ProviderCatalogSnapshot> marketplace = loadMarketplaceCache();
        if (marketplace.isPresent()) {
            return marketplace.get();
        }
        ClassPathResource resource = new ClassPathResource(CATALOG_RESOURCE);
        if (!resource.exists()) {
            return fallback("builtin-fallback");
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return loadFromJson(
                    new String(inputStream.readAllBytes(), StandardCharsets.UTF_8),
                    "classpath:" + CATALOG_RESOURCE
            );
        } catch (IOException | IllegalArgumentException exception) {
            return fallback("catalog-load-fallback:" + exception.getClass().getSimpleName());
        }
    }

    public ProviderCatalogSnapshot loadFromJson(String catalogJson, String sourceOverride) {
        try {
            JsonNode root = objectMapper.readTree(catalogJson);
            String version = text(root, "catalogVersion", "2026.05.01-local");
            String source = sourceOverride == null || sourceOverride.isBlank()
                    ? text(root, "catalogSource", "inline")
                    : sourceOverride;
            List<ProviderPresetDefinition> presets = new ArrayList<>();
            Set<String> seenCodes = new LinkedHashSet<>();
            JsonNode presetNodes = root.path("presets");
            if (presetNodes.isArray()) {
                for (JsonNode item : presetNodes) {
                    ProviderPresetDefinition preset = toPreset(item, version, source);
                    if (!seenCodes.add(preset.code())) {
                        throw new IllegalArgumentException("provider catalog preset code 重复：" + preset.code());
                    }
                    presets.add(preset);
                }
            }
            if (presets.isEmpty()) {
                throw new IllegalArgumentException("provider catalog 至少需要一个 preset。");
            }
            return new ProviderCatalogSnapshot(version, source, presets);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("provider catalog JSON 无法解析。", exception);
        }
    }

    private Optional<ProviderCatalogSnapshot> loadMarketplaceCache() {
        if (marketplaceCurrentPath == null || !Files.exists(marketplaceCurrentPath)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(marketplaceCurrentPath, StandardCharsets.UTF_8);
            return Optional.of(loadFromJson(content, "marketplace-cache:" + marketplaceCurrentPath.toAbsolutePath()));
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private Path marketplaceRoot(GatewayProperties gatewayProperties) {
        return Path.of(gatewayProperties.getStorage().getFileRoot()).toAbsolutePath()
                .resolve("provider-catalog-marketplace");
    }

    private ProviderPresetDefinition toPreset(JsonNode node, String version, String source) {
        List<String> conformanceChecks = stringList(node.path("conformanceChecks"));
        if (conformanceChecks.isEmpty()) {
            throw new IllegalArgumentException("provider catalog preset 至少需要一个 conformanceChecks。");
        }
        return new ProviderPresetDefinition(
                requiredText(node, "code"),
                requiredText(node, "displayName"),
                text(node, "vendorCode", null),
                text(node, "vendorName", null),
                UpstreamSiteKind.valueOf(requiredText(node, "siteKind")),
                requiredText(node, "defaultBaseUrl"),
                text(node, "description", ""),
                stringList(node.path("capabilityTags")),
                text(node, "costProfile", "provider-compatible"),
                text(node, "errorMode", "openai_error"),
                version,
                source,
                node.path("deprecated").asBoolean(false),
                conformanceChecks,
                text(node, "compatibilitySurface", "openai-compatible-chat"),
                text(node, "supportStrategy", "cloud-openai-compatible"),
                stringList(node.path("modelFamilies")),
                text(node, "pricingMetadata", ""),
                stringList(node.path("unsupportedFeatures")),
                objectMap(node.path("conversationProfile")),
                objectList(node.path("modelPolicies"))
        );
    }

    private ProviderCatalogSnapshot fallback(String source) {
        String version = "2026.05.01-fallback";
        return new ProviderCatalogSnapshot(version, source, List.of(
                preset("openai", "OpenAI", UpstreamSiteKind.OPENAI_DIRECT, "https://api.openai.com",
                        "OpenAI 对话与 tools 功能性服务 API，覆盖 Chat、Responses、Embeddings、Audio、Images、Moderation、Files、Uploads、Models 与 Vector Stores 支撑面；官方非核心 API 不纳入当前公开范围。",
                        List.of("chat", "responses", "embeddings", "audio", "images", "moderation", "files", "models", "vector_stores"),
                        "openai-public-pricing", "openai_error", version, source, List.of("chat.native", "responses.emulated", "files.native"),
                        "openai-native", "native-first",
                        List.of("gpt-4.1", "gpt-4o", "o-series", "text-embedding", "gpt-image"),
                        "public-list-price-openai", List.of()),
                preset("azure_openai", "Azure OpenAI", UpstreamSiteKind.AZURE_OPENAI, "https://{resource}.openai.azure.com",
                        "Azure OpenAI deployment-style endpoint，需要按 deployment name 寻址。",
                        List.of("chat", "responses", "azure_api_key", "deployment_name"),
                        "azure-openai-metered", "azure_openai_error", version, source, List.of("chat.native", "deployment-addressing.native"),
                        "openai-compatible-chat", "deployment-addressing-required",
                        List.of("gpt-4o", "gpt-4.1", "text-embedding"), "azure-metered-pricing",
                        List.of()),
                preset("deepseek", "DeepSeek", UpstreamSiteKind.DEEPSEEK, "https://api.deepseek.com",
                        "DeepSeek OpenAI-compatible API，适合作为 OpenAI-style chat 与 reasoning 兼容站点。",
                        List.of("chat", "openai_compatible", "reasoning"),
                        "provider-compatible", "openai_error", version, source, List.of("chat.native", "openai-compatible.surface"),
                        "openai-compatible-chat", "cloud-openai-compatible",
                        List.of("deepseek-chat", "deepseek-reasoner"), "provider-console-pricing",
                        List.of("file_object: current gateway does not generalize OpenAI-compatible object lifecycle.")),
                preset("openrouter", "OpenRouter", UpstreamSiteKind.OPENROUTER, "https://openrouter.ai/api/v1",
                        "OpenRouter 聚合站 OpenAI-compatible API，后续可承载多供应商模型路由与成本 metadata。",
                        List.of("chat", "openai_compatible", "aggregator"),
                        "aggregator-provider-pricing", "openai_error", version, source, List.of("chat.native", "aggregator.routing"),
                        "openai-compatible-chat", "aggregator-routing",
                        List.of("openrouter-auto", "byok-routing"), "aggregator-pass-through-pricing",
                        List.of()),
                preset("anthropic", "Anthropic", UpstreamSiteKind.ANTHROPIC_DIRECT, "https://api.anthropic.com",
                        "Anthropic Messages API，仅按 OpenAI 标准功能区收紧为 chat/tools/thinking 入口，不追求 Anthropic 全量官方 API。",
                        List.of("chat", "tools", "thinking", "anthropic_native"),
                        "anthropic-public-pricing", "anthropic_error", version, source, List.of("messages.native"),
                        "anthropic-native", "translation-layer",
                        List.of("claude-sonnet", "claude-opus"), "public-list-price-anthropic",
                        List.of(
                                "embeddings: Anthropic stable embeddings API is not exposed in current gateway.",
                                "non_core_provider_apis: provider-specific async/admin/eval APIs are outside the OpenAI standard functional zone.")),
                preset("gemini", "Gemini", UpstreamSiteKind.GEMINI_DIRECT, "https://generativelanguage.googleapis.com",
                        "Google Gemini API，仅按 OpenAI 标准功能区收紧为 generateContent、embeddings 与 files 支撑面，不追求 Gemini 全量官方 API。",
                        List.of("chat", "embeddings", "files", "google_native"),
                        "gemini-public-pricing", "gemini_error", version, source, List.of("generate-content.native", "files.orchestrated"),
                        "google-native", "translation-layer",
                        List.of("gemini-2.5-pro", "gemini-2.5-flash", "text-embedding"), "public-list-price-gemini",
                        List.of("non_core_provider_apis: provider-specific async/admin/eval APIs are outside the OpenAI standard functional zone."))
        ));
    }

    private ProviderPresetDefinition preset(
            String code,
            String displayName,
            UpstreamSiteKind siteKind,
            String defaultBaseUrl,
            String description,
            List<String> capabilityTags,
            String costProfile,
            String errorMode,
            String version,
            String source,
            List<String> conformanceChecks,
            String compatibilitySurface,
            String supportStrategy,
            List<String> modelFamilies,
            String pricingMetadata,
            List<String> unsupportedFeatures) {
        return new ProviderPresetDefinition(
                code,
                displayName,
                null,
                null,
                siteKind,
                defaultBaseUrl,
                description,
                capabilityTags,
                costProfile,
                errorMode,
                version,
                source,
                false,
                conformanceChecks,
                compatibilitySurface,
                supportStrategy,
                modelFamilies,
                pricingMetadata,
                unsupportedFeatures,
                Map.of(),
                List.of()
        );
    }

    private Map<String, Object> objectMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, Map.class);
    }

    private List<Map<String, Object>> objectList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item != null && item.isObject()) {
                values.add(objectMapper.convertValue(item, Map.class));
            }
        }
        return List.copyOf(values);
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = text(node, fieldName, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("provider catalog 缺少字段：" + fieldName);
        }
        return value;
    }

    private String text(JsonNode node, String fieldName, String fallback) {
        JsonNode value = node == null ? null : node.path(fieldName);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? fallback : text;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText(null);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }
}
