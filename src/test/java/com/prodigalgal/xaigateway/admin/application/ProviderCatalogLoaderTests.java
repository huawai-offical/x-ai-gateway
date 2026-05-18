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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderCatalogLoaderTests {

    @Test
    void shouldLoadExpandedProviderPresetMetadataFromClasspath() {
        ProviderCatalogSnapshot snapshot = new ProviderCatalogLoader(new ObjectMapper()).load();

        assertTrue(snapshot.presets().size() >= 15);

        ProviderPresetDefinition openai = snapshot.presets().stream()
                .filter(item -> item.code().equals("openai"))
                .findFirst()
                .orElseThrow();
        ProviderPresetDefinition qwen = snapshot.presets().stream()
                .filter(item -> item.code().equals("qwen"))
                .findFirst()
                .orElseThrow();
        ProviderPresetDefinition jina = snapshot.presets().stream()
                .filter(item -> item.code().equals("jina"))
                .findFirst()
                .orElseThrow();
        ProviderPresetDefinition dify = snapshot.presets().stream()
                .filter(item -> item.code().equals("dify"))
                .findFirst()
                .orElseThrow();
        ProviderPresetDefinition xai = snapshot.presets().stream()
                .filter(item -> item.code().equals("xai"))
                .findFirst()
                .orElseThrow();
        ProviderPresetDefinition perplexity = snapshot.presets().stream()
                .filter(item -> item.code().equals("perplexity"))
                .findFirst()
                .orElseThrow();
        ProviderPresetDefinition vertex = snapshot.presets().stream()
                .filter(item -> item.code().equals("vertex"))
                .findFirst()
                .orElseThrow();

        assertEquals(UpstreamSiteKind.OPENAI_DIRECT, openai.siteKind());
        assertEquals("openai-native", openai.compatibilitySurface());
        assertTrue(openai.conformanceChecks().contains("openai.responses-native-stream-sse-passthrough"));
        assertTrue(openai.conformanceChecks().contains("openai.direct-smoke-certification"));
        assertTrue(openai.conformanceChecks().contains("openai.responses-untracked-remote-lifecycle-route-hints"));
        assertTrue(openai.conformanceChecks().contains("openai.direct-smoke-record-replay-fixture"));
        assertTrue(openai.conformanceChecks().contains("openai.direct-smoke-record-replay-ci-verifier"));
        assertTrue(openai.conformanceChecks().contains("openai.conversations-local-lifecycle"));
        assertTrue(openai.conformanceChecks().contains("openai.vector-stores-local-lifecycle"));
        assertTrue(openai.conformanceChecks().contains("openai.vector-store-files-local-attachment"));
        assertTrue(openai.conformanceChecks().contains("openai.vector-store-file-content-local-read"));
        assertTrue(openai.conformanceChecks().contains("openai.vector-store-search-local-text"));
        assertTrue(openai.conformanceChecks().contains("openai.responses-file-search-local-vector-store-binding"));
        assertTrue(openai.conformanceChecks().contains("openai.vector-store-file-batches-local-lifecycle"));
        assertTrue(openai.conformanceChecks().contains("openai.webhooks-ingress-event-persistence"));
        assertTrue(openai.conformanceChecks().contains("openai.batches-list-local-catalog"));
        assertTrue(openai.conformanceChecks().contains("openai.models-delete-local-registry"));
        assertTrue(openai.conformanceChecks().contains("openai.fine-tuning-events-checkpoints-local-lineage"));
        assertTrue(openai.capabilityTags().contains("fine_tuning"));
        assertTrue(openai.unsupportedFeatures().stream().anyMatch(item -> item.contains("administration_api")));
        assertTrue(openai.unsupportedFeatures().stream().anyMatch(item -> item.contains("fine_tuning_pause_resume_permissions")));
        assertTrue(openai.unsupportedFeatures().stream().anyMatch(item -> item.contains("models_delete_upstream_owner_passthrough")));
        assertTrue(openai.unsupportedFeatures().stream().anyMatch(item -> item.contains("responses_non_function_tools")));
        assertTrue(openai.unsupportedFeatures().stream().anyMatch(item -> item.contains("vector_stores_child_resources")));
        assertTrue(openai.unsupportedFeatures().stream().anyMatch(item -> item.contains("realtime_full_calls_webrtc_sip")));
        assertTrue(openai.conformanceChecks().contains("openai.responses-tool-registry-boundary"));
        assertTrue(openai.capabilityTags().contains("conversations"));
        assertTrue(openai.capabilityTags().contains("vector_stores"));
        assertTrue(openai.capabilityTags().contains("webhooks"));

        assertEquals(UpstreamSiteKind.QWEN, qwen.siteKind());
        assertEquals("openai-compatible-chat", qwen.compatibilitySurface());
        assertEquals("cloud-openai-compatible", qwen.supportStrategy());
        assertTrue(qwen.modelFamilies().contains("qwen3"));
        assertTrue(qwen.unsupportedFeatures().stream().anyMatch(item -> item.contains("realtime_client_secret")));

        assertEquals(UpstreamSiteKind.JINA, jina.siteKind());
        assertEquals("rerank-native", jina.compatibilitySurface());
        assertEquals("native-rerank", jina.supportStrategy());
        assertTrue(jina.capabilityTags().contains("rerank"));

        assertEquals(UpstreamSiteKind.DIFY, dify.siteKind());
        assertEquals("dify-compatible", dify.compatibilitySurface());
        assertEquals("workflow-openai-compatible", dify.supportStrategy());

        assertEquals(UpstreamSiteKind.GROK, xai.siteKind());
        assertEquals("openai-compatible-chat", xai.compatibilitySurface());
        assertTrue(xai.capabilityTags().contains("xai"));
        assertTrue(xai.modelFamilies().contains("grok-4.3"));
        assertTrue(xai.unsupportedFeatures().stream().anyMatch(item -> item.contains("2026-05-15")));

        assertEquals(UpstreamSiteKind.PERPLEXITY, perplexity.siteKind());
        assertEquals("openai-compatible-web-search", perplexity.compatibilitySurface());
        assertEquals("web-search-openai-compatible", perplexity.supportStrategy());
        assertTrue(perplexity.capabilityTags().contains("web_search"));

        assertEquals(UpstreamSiteKind.VERTEX_AI, vertex.siteKind());
        assertEquals("google-native", vertex.compatibilitySurface());
        assertEquals("vertex-google-native", vertex.supportStrategy());
        assertTrue(vertex.capabilityTags().contains("project_location"));
    }

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
