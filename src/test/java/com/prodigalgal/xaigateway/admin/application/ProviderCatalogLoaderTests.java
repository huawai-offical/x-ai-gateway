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
            assertTrue(publicPresetCodes.contains(preset.code()), preset.code() + " public docs preset");
            assertTrue(fixtureSiteKinds.contains(preset.siteKind().name()), preset.code() + " conformance fixture");
        }
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
