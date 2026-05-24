package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCatalogLoaderTests {


    @Test
    void shouldRejectDuplicatePresetCodes() {
        ProviderCatalogLoader loader = new ProviderCatalogLoader(new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> loader.loadFromJson("""
                {
                  "catalogVersion": "test",
                  "presets": [
                    {
                      "code": "qwen",
                      "displayName": "Qwen A",
                      "siteKind": "QWEN",
                      "defaultBaseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
                      "conformanceChecks": ["chat.native"]
                    },
                    {
                      "code": "qwen",
                      "displayName": "Qwen B",
                      "siteKind": "QWEN",
                      "defaultBaseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
                      "conformanceChecks": ["chat.native"]
                    }
                  ]
                }
                """, "inline"));
    }

    @Test
    void shouldRejectPresetWithoutConformanceChecks() {
        ProviderCatalogLoader loader = new ProviderCatalogLoader(new ObjectMapper());

        assertThrows(IllegalArgumentException.class, () -> loader.loadFromJson("""
                {
                  "catalogVersion": "test",
                  "presets": [
                    {
                      "code": "jina",
                      "displayName": "Jina",
                      "siteKind": "JINA",
                      "defaultBaseUrl": "https://api.jina.ai/v1"
                    }
                  ]
                }
                """, "inline"));
    }

    @Test
    void shouldKeepPricingMetadataPublicDocsAndConformanceFixturesInSync() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ProviderCatalogSnapshot snapshot = new ProviderCatalogLoader(objectMapper).load();
        Set<String> publicPresetCodes = new PublicDocsBundleService().bundle("zh-CN")
                .providerPresets()
                .stream()
                .map(item -> item.code())
                .collect(Collectors.toSet());
        Set<String> fixtureSiteKinds = fixtureSiteKinds(objectMapper);

        for (ProviderPresetDefinition preset : snapshot.presets()) {
            assertTrue(!preset.costProfile().isBlank(), preset.code() + " costProfile");
            assertTrue(!preset.pricingMetadata().isBlank(), preset.code() + " pricingMetadata");
            assertTrue(!preset.capabilityTags().isEmpty(), preset.code() + " capabilityTags");
            assertTrue(!preset.conformanceChecks().isEmpty(), preset.code() + " conformanceChecks");
            assertNativeAdapterContract(preset);
            assertTrue(publicPresetCodes.contains(preset.code()), preset.code() + " public docs preset");
            assertTrue(fixtureSiteKinds.contains(preset.siteKind().name()), preset.code() + " conformance fixture");
        }
    }

    @Test
    void shouldExposeOnlyHeadOrSelfModelProviderPresetsByDefault() {
        ProviderCatalogSnapshot snapshot = new ProviderCatalogLoader(new ObjectMapper()).load();
        Set<String> presetCodes = snapshot.presets().stream()
                .map(ProviderPresetDefinition::code)
                .collect(Collectors.toSet());

        assertTrue(presetCodes.containsAll(Set.of(
                "openai",
                "azure_openai",
                "xiaomi_mimo",
                "deepseek",
                "qwen",
                "moonshot",
                "volcengine",
                "minimax",
                "xai",
                "perplexity",
                "cohere",
                "jina",
                "mistral",
                "anthropic",
                "gemini",
                "vertex"
        )));
        assertFalse(presetCodes.contains("openai_compatible_generic"));
        assertFalse(presetCodes.contains("dify"));
        assertFalse(presetCodes.contains("openrouter"));
        assertFalse(presetCodes.contains("siliconflow"));
        assertFalse(presetCodes.contains("together"));
        assertFalse(presetCodes.contains("fireworks"));
    }

    private void assertNativeAdapterContract(ProviderPresetDefinition preset) {
        var contract = preset.nativeAdapterContract();
        assertTrue(!contract.isEmpty(), preset.code() + " nativeAdapterContract");
        assertTrue(textValue(contract.get("adapterKind")) != null, preset.code() + " adapterKind");
        assertTrue(listValue(contract.get("nativeProtocols")), preset.code() + " nativeProtocols");
        assertTrue(listValue(contract.get("requiredEndpoints")), preset.code() + " requiredEndpoints");
        assertTrue(textValue(contract.get("auth")) != null, preset.code() + " auth");
        assertTrue(textValue(contract.get("stream")) != null, preset.code() + " stream");
        assertTrue(textValue(contract.get("tools")) != null, preset.code() + " tools");
        assertTrue(textValue(contract.get("usage")) != null, preset.code() + " usage");
        assertTrue(textValue(contract.get("errorMapping")) != null, preset.code() + " errorMapping");
        assertEquals("native_required", textValue(contract.get("smokeClassification")), preset.code() + " smokeClassification");
    }

    private String textValue(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return null;
        }
        return text;
    }

    private boolean listValue(Object value) {
        return value instanceof java.util.List<?> list && !list.isEmpty();
    }

    private Set<String> fixtureSiteKinds(ObjectMapper objectMapper) throws IOException {
        try (InputStream inputStream = ProviderCatalogLoaderTests.class.getClassLoader()
                .getResourceAsStream("conformance/site-conformance-fixtures.json")) {
            if (inputStream == null) {
                throw new IOException("缺少 conformance/site-conformance-fixtures.json");
            }
            JsonNode root = objectMapper.readTree(inputStream);
            java.util.HashSet<String> siteKinds = new java.util.HashSet<>();
            for (JsonNode item : root) {
                String siteKind = item.path("siteKind").asText("");
                if (!siteKind.isBlank()) {
                    siteKinds.add(siteKind);
                }
            }
            return siteKinds;
        }
    }
}
