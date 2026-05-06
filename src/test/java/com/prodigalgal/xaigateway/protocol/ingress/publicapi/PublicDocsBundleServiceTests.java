package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicDocsBundleServiceTests {

    @Test
    void shouldReturnChineseCompatibilityBundleByDefault() {
        PublicDocsBundleService service = new PublicDocsBundleService();

        PublicDocsBundleResponse response = service.bundle(null);

        assertEquals("zh-CN", response.locale());
        assertTrue(response.quickStart().stream().anyMatch(item -> item.contains("Distributed Key")));
        assertTrue(response.compatibility().stream().anyMatch(item -> "openai".equals(item.protocol())));
        assertTrue(response.compatibility().stream().anyMatch(item -> "rerank".equals(item.protocol())));
        assertTrue(response.providerPresets().stream().anyMatch(item -> "qwen".equals(item.code())));
        assertTrue(response.providerPresets().stream().anyMatch(item -> "jina".equals(item.code()) && "rerank-native".equals(item.compatibilitySurface())));
        assertTrue(response.cliClients().stream().anyMatch(item -> "CODEX".equals(item.clientFamily())));
        assertTrue(response.cliClients().stream().anyMatch(item -> "CURSOR".equals(item.clientFamily())));
        assertTrue(response.cliClients().stream().allMatch(item ->
                item.notes().stream().anyMatch(note -> note.contains("不需要在用户机器上部署本地 proxy"))));
        assertEquals("/public/docs/openapi.json", response.openApiUrl());
        assertTrue(response.sdkTargets().contains("Codex CLI"));
        assertTrue(response.i18nPolicy().stream().anyMatch(item -> item.contains("zh-CN")));
        assertTrue(response.examples().stream().anyMatch(item -> "openai-sdk".equals(item.client())));
        assertTrue(response.examples().stream().anyMatch(item -> "codex-cli".equals(item.client())));
        assertTrue(response.errorCodes().stream().anyMatch(item -> "rate_limit_exceeded".equals(item.code())));
        assertTrue(response.conformanceChecks().contains("ollama.chat.native"));
    }

    @Test
    void shouldReturnEnglishCompatibilityBundle() {
        PublicDocsBundleService service = new PublicDocsBundleService();

        PublicDocsBundleResponse response = service.bundle("en-US");

        assertEquals("en-US", response.locale());
        assertTrue(response.title().contains("Public Compatibility"));
        assertTrue(response.quickStart().stream().anyMatch(item -> item.contains("Authorization Bearer")));
        assertTrue(response.providerPresets().stream().anyMatch(item -> "dify".equals(item.code())));
        assertTrue(response.cliClients().stream().anyMatch(item -> "GitHub Copilot-compatible".equals(item.client())));
        assertTrue(response.billingNotes().stream().anyMatch(item -> item.contains("billing rollup")));
        assertTrue(response.i18nPolicy().stream().anyMatch(item -> item.contains("public docs bundle")));
    }

    @Test
    void shouldExposeMinimalPublicOpenApiSpec() {
        PublicDocsBundleService service = new PublicDocsBundleService();

        var openApi = service.openApi();

        assertEquals("3.1.0", openApi.path("openapi").asText());
        assertEquals("x-ai-gateway Public API", openApi.path("info").path("title").asText());
        assertTrue(openApi.path("paths").has("/public/docs/compatibility"));
        assertTrue(openApi.path("paths").has("/public/docs/openapi.json"));
        assertTrue(openApi.path("paths").has("/v1/chat/completions"));
        assertTrue(openApi.path("paths").has("/api/v1/media/provider-matrix"));
        assertTrue(openApi.path("components").path("securitySchemes").has("bearerAuth"));
    }
}
