package com.prodigalgal.xaigateway.docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicOpenApiSnapshotTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldKeepPublicOpenApiSnapshotPublishable() throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(Files.readString(Path.of("docs/openapi/public-openapi.json")));

        assertEquals("3.1.0", root.path("openapi").asText());
        assertFalse(root.path("info").path("title").asText().isBlank());
        assertFalse(root.path("info").path("version").asText().isBlank());
        assertTrue(root.path("paths").has("/public/docs/openapi.json"));
        assertTrue(root.path("paths").has("/v1/chat/completions"));
        assertTrue(root.path("paths").has("/v1/chat/completions/{completionId}"));
        assertTrue(root.path("paths").has("/v1/chat/completions/{completionId}/messages"));
        assertTrue(root.path("paths").has("/v1/responses"));
        assertTrue(root.path("paths").has("/v1/responses/{responseId}"));
        assertTrue(root.path("paths").has("/v1/responses/{responseId}/cancel"));
        assertTrue(root.path("paths").has("/v1/responses/{responseId}/input_items"));
        JsonNode chatProperties = root.path("paths")
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
        assertTrue(chatProperties.has("modalities"));
        assertTrue(chatProperties.has("audio"));
        assertEquals("Idempotency-Key", root.path("paths")
                .path("/v1/chat/completions")
                .path("post")
                .path("parameters")
                .path(0)
                .path("name")
                .asText());
        assertEquals("Idempotency-Key", root.path("paths")
                .path("/v1/responses")
                .path("post")
                .path("parameters")
                .path(0)
                .path("name")
                .asText());
        assertTrue(hasParameter(root.path("paths").path("/v1/chat/completions").path("get"), "after"));
        assertTrue(hasParameter(root.path("paths").path("/v1/chat/completions").path("get"), "limit"));
        assertTrue(hasParameter(root.path("paths").path("/v1/chat/completions").path("get"), "order"));
        assertTrue(hasParameter(root.path("paths").path("/v1/chat/completions").path("get"), "model"));
        assertTrue(hasParameter(root.path("paths").path("/v1/chat/completions").path("get"), "metadata[key]"));
        assertTrue(hasParameter(root.path("paths").path("/v1/chat/completions/{completionId}/messages").path("get"), "completionId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/chat/completions/{completionId}/messages").path("get"), "after"));
        assertTrue(hasParameter(root.path("paths").path("/v1/chat/completions/{completionId}/messages").path("get"), "limit"));
        assertTrue(hasParameter(root.path("paths").path("/v1/chat/completions/{completionId}/messages").path("get"), "order"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}").path("get"), "responseId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/cancel").path("post"), "responseId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "responseId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "after"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "limit"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "order"));
        assertTrue(root.path("components").path("securitySchemes").has("bearerAuth"));
    }

    @Test
    void shouldExposeSdkExampleRegistryForAllSupportedTargets() throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(Files.readString(Path.of("docs/sdk-examples/index.json")));
        JsonNode targets = root.path("targets");

        assertTrue(hasTarget(targets, "python", "python/chat_completions.py"));
        assertTrue(hasTarget(targets, "javascript", "javascript/chat-completions.mjs"));
        assertTrue(hasTarget(targets, "javascript", "javascript/chat-advanced-parameters.mjs"));
        assertTrue(hasTarget(targets, "go", "go/chat_completions.go"));
        assertTrue(hasTarget(targets, "java", "java/ChatCompletionsExample.java"));
    }

    private boolean hasTarget(JsonNode targets, String language, String file) {
        for (JsonNode target : targets) {
            if (language.equals(target.path("language").asText()) && file.equals(target.path("file").asText())) {
                return true;
            }
        }
        return false;
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
