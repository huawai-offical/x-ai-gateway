package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

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
        assertTrue(response.compatibility().stream().anyMatch(item -> "web_search".equals(item.protocol())));
        assertTrue(response.providerPresets().stream().anyMatch(item -> "qwen".equals(item.code())));
        assertTrue(response.providerPresets().stream().anyMatch(item -> "jina".equals(item.code()) && "rerank-native".equals(item.compatibilitySurface())));
        assertTrue(response.providerPresets().stream().anyMatch(item -> "perplexity".equals(item.code()) && item.capabilityTags().contains("web_search")));
        assertTrue(response.cliClients().stream().anyMatch(item -> "CODEX".equals(item.clientFamily())));
        assertTrue(response.cliClients().stream().anyMatch(item -> "CURSOR".equals(item.clientFamily())));
        assertTrue(response.cliClients().stream().allMatch(item ->
                item.notes().stream().anyMatch(note -> note.contains("不需要在用户机器上部署本地 proxy"))));
        assertEquals("/public/docs/openapi.json", response.openApiUrl());
        assertTrue(response.sdkTargets().contains("Codex CLI"));
        assertTrue(response.i18nPolicy().stream().anyMatch(item -> item.contains("zh-CN")));
        assertTrue(response.examples().stream().anyMatch(item -> "openai-sdk".equals(item.client())));
        assertTrue(response.examples().stream().anyMatch(item -> "openai-sdk-advanced-chat".equals(item.client())));
        assertTrue(response.examples().stream().anyMatch(item -> "codex-cli".equals(item.client())));
        assertTrue(response.errorCodes().stream().anyMatch(item -> "rate_limit_exceeded".equals(item.code())));
        assertTrue(response.routingNotes().stream().anyMatch(item -> item.contains("x-ratelimit")));
        assertTrue(response.conformanceChecks().contains("ollama.chat.native"));
        assertTrue(response.conformanceChecks().contains("chat.openai-typed-parameters"));
        assertTrue(response.conformanceChecks().contains("openai.list-pagination-envelope"));
        assertTrue(response.conformanceChecks().contains("openai.idempotency-replay"));
        assertTrue(response.conformanceChecks().contains("openai.responses-local-lifecycle"));
        assertTrue(response.conformanceChecks().contains("openai.streaming-event-usage-sequence"));
        assertTrue(response.conformanceChecks().contains("openai.webhook-signature-replay"));
        assertTrue(response.compatibility().stream().anyMatch(item ->
                "openai".equals(item.protocol()) && item.supportedOperations().contains("chat.typed-parameters")));
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
        assertTrue(openApi.path("paths").has("/v1/chat/completions/{completionId}"));
        assertTrue(openApi.path("paths").has("/v1/chat/completions/{completionId}/messages"));
        assertTrue(openApi.path("paths").has("/v1/responses/{responseId}"));
        assertTrue(openApi.path("paths").has("/v1/responses/{responseId}/cancel"));
        assertTrue(openApi.path("paths").has("/v1/responses/{responseId}/input_items"));
        assertTrue(openApi.path("paths").has("/v1/web_search"));
        assertTrue(openApi.path("paths").has("/api/v1/media/provider-matrix"));
        var chatProperties = openApi.path("paths")
                .path("/v1/chat/completions")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties");
        assertTrue(chatProperties.has("response_format"));
        assertTrue(chatProperties.has("tools"));
        assertTrue(chatProperties.has("tool_choice"));
        assertTrue(chatProperties.has("store"));
        assertTrue(chatProperties.has("metadata"));
        assertTrue(chatProperties.has("web_search_options"));
        assertTrue(openApi.path("paths")
                .path("/v1/chat/completions")
                .path("post")
                .path("parameters")
                .path(0)
                .path("name")
                .asText()
                .equals("Idempotency-Key"));
        assertTrue(openApi.path("paths")
                .path("/v1/responses")
                .path("post")
                .path("parameters")
                .path(0)
                .path("name")
                .asText()
                .equals("Idempotency-Key"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/chat/completions").path("get"), "after"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/chat/completions").path("get"), "limit"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/chat/completions").path("get"), "order"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/chat/completions").path("get"), "model"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/chat/completions").path("get"), "metadata[key]"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/chat/completions/{completionId}/messages").path("get"), "completionId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/chat/completions/{completionId}/messages").path("get"), "after"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/chat/completions/{completionId}/messages").path("get"), "limit"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/chat/completions/{completionId}/messages").path("get"), "order"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}").path("get"), "responseId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}/cancel").path("post"), "responseId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "responseId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "after"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "limit"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "order"));
        assertTrue(openApi.path("components").path("securitySchemes").has("bearerAuth"));
    }

    private boolean hasParameter(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asText())) {
                return true;
            }
        }
        return false;
    }
}
