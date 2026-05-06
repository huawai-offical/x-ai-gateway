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
        assertTrue(root.path("paths").has("/v1/responses"));
        assertTrue(root.path("components").path("securitySchemes").has("bearerAuth"));
    }

    @Test
    void shouldExposeSdkExampleRegistryForAllSupportedTargets() throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(Files.readString(Path.of("docs/sdk-examples/index.json")));
        JsonNode targets = root.path("targets");

        assertTrue(hasTarget(targets, "python", "python/chat_completions.py"));
        assertTrue(hasTarget(targets, "javascript", "javascript/chat-completions.mjs"));
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
}
