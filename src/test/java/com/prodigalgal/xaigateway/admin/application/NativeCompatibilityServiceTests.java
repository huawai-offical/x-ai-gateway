package com.prodigalgal.xaigateway.admin.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeCompatibilityServiceTests {

    @Test
    void shouldExposeNativeCompatibilityMatrix() {
        NativeCompatibilityService service = new NativeCompatibilityService();

        var response = service.matrix();

        assertTrue(response.routes().stream().anyMatch(route -> route.protocol().equals("ollama") && route.path().equals("/ollama/api/chat")));
        assertTrue(response.routes().stream().anyMatch(route -> route.protocol().equals("anthropic") && route.status().equals("EXPLICIT_UNSUPPORTED")));
        assertTrue(response.routes().stream().anyMatch(route -> route.namespace().equals("/google/upload/v1beta") && route.path().equals("/google/upload/v1beta/files")));
        assertTrue(response.routes().stream().anyMatch(route -> route.namespace().equals("/v1beta") && route.status().equals("SUPPORTED_GOVERNED")));
        assertTrue(response.routes().stream().anyMatch(route -> route.protocol().equals("vertex")
                && route.status().equals("CATALOG_GOVERNED")
                && route.notes().contains("标准功能区")));
        assertTrue(response.routes().stream().anyMatch(route -> route.protocol().equals("codex")
                && route.status().equals("SMOKE_ONLY")
                && route.path().equals("/backend-api/codex/responses")));
        assertTrue(response.routes().stream().allMatch(route -> route.authenticated()));
        assertTrue(response.translationConformance().stream().anyMatch(row -> row.provider().equals("OpenAI") && row.supportLevel().equals("native")));
        assertTrue(response.translationConformance().stream().anyMatch(row -> row.provider().equals("Anthropic") && row.supportLevel().equals("lossy")));
        assertTrue(response.translationConformance().stream().anyMatch(row -> row.provider().equals("xAI / Perplexity")
                && row.supportLevel().equals("partial")
                && row.supportedFeatures().contains("Perplexity search-augmented chat")));
        assertTrue(response.translationConformance().stream().anyMatch(row -> row.provider().equals("Vertex AI")
                && row.unsupportedFeatures().contains("provider non-standard async APIs")));
        assertTrue(response.translationConformance().stream().anyMatch(row -> row.provider().equals("Codex App API")
                && row.supportedFeatures().contains("Responses request body")
                && row.supportedFeatures().contains("dry-run preview")
                && row.lossyFeatures().contains("record/replay fixture hardening")));
        assertTrue(response.translationConformance().stream().allMatch(row -> !row.verification().isBlank()));
    }
}
