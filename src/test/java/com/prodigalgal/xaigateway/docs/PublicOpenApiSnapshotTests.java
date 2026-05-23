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
        assertTrue(root.path("paths").has("/v1/responses/input_tokens"));
        assertTrue(root.path("paths").has("/v1/responses/compact"));
        assertTrue(root.path("paths").has("/v1/responses/{responseId}"));
        assertTrue(root.path("paths").has("/v1/responses/{responseId}/cancel"));
        assertTrue(root.path("paths").has("/v1/responses/{responseId}/input_items"));
        assertTrue(root.path("paths").has("/v1/conversations"));
        assertTrue(root.path("paths").has("/v1/conversations/{conversationId}"));
        assertTrue(root.path("paths").has("/v1/conversations/{conversationId}/items"));
        assertTrue(root.path("paths").has("/v1/conversations/{conversationId}/items/{itemId}"));
        assertTrue(root.path("paths").has("/v1/vector_stores"));
        assertTrue(root.path("paths").has("/v1/vector_stores/{vectorStoreId}"));
        assertTrue(root.path("paths").has("/v1/vector_stores/{vectorStoreId}/search"));
        assertTrue(root.path("paths").has("/v1/vector_stores/{vectorStoreId}/files"));
        assertTrue(root.path("paths").has("/v1/vector_stores/{vectorStoreId}/files/{fileId}"));
        assertTrue(root.path("paths").has("/v1/vector_stores/{vectorStoreId}/files/{fileId}/content"));
        assertTrue(root.path("paths").has("/v1/vector_stores/{vectorStoreId}/file_batches"));
        assertTrue(root.path("paths").has("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}"));
        assertTrue(root.path("paths").has("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/cancel"));
        assertTrue(root.path("paths").has("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files"));
        assertTrue(root.path("paths").has("/v1/webhooks/openai"));
        assertTrue(root.path("paths").has("/v1/models"));
        assertTrue(root.path("paths").has("/v1/models/{model}"));
        assertFalse(root.path("paths").path("/v1/models/{model}").has("delete"));
        assertTrue(root.path("paths").has("/v1/audio/translations"));
        assertTrue(root.path("paths").has("/v1/images/edits"));
        assertTrue(root.path("paths").has("/v1/images/variations"));
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
        JsonNode responsesProperties = root.path("paths")
                .path("/v1/responses")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties");
        assertTrue(responsesProperties.has("stream_options"));
        assertTrue(responsesProperties.path("stream_options").path("description").asText().contains("include_obfuscation"));
        assertTrue(responsesProperties.has("tools"));
        assertTrue(responsesProperties.path("tools").path("description").asText().contains("file_search"));
        assertTrue(responsesProperties.has("tool_choice"));
        assertTrue(responsesProperties.path("tool_choice").path("description").asText().contains("Non-function"));
        assertTrue(root.path("paths")
                .path("/v1/responses/input_tokens")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("input"));
        assertTrue(root.path("paths")
                .path("/v1/responses/compact")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("input"));
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
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}").path("get"), "include"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}").path("get"), "model"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}").path("get"), "X-AI-Gateway-OpenAI-Model"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}").path("delete"), "model"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}").path("delete"), "X-AI-Gateway-OpenAI-Model"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/cancel").path("post"), "responseId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/cancel").path("post"), "model"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/cancel").path("post"), "X-AI-Gateway-OpenAI-Model"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "responseId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "after"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "include"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "limit"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "order"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "model"));
        assertTrue(hasParameter(root.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "X-AI-Gateway-OpenAI-Model"));
        assertTrue(hasParameter(root.path("paths").path("/v1/conversations/{conversationId}").path("get"), "conversationId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/conversations/{conversationId}/items").path("post"), "conversationId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/conversations/{conversationId}/items").path("post"), "include"));
        assertTrue(hasParameter(root.path("paths").path("/v1/conversations/{conversationId}/items").path("get"), "after"));
        assertTrue(hasParameter(root.path("paths").path("/v1/conversations/{conversationId}/items").path("get"), "include"));
        assertTrue(hasParameter(root.path("paths").path("/v1/conversations/{conversationId}/items").path("get"), "limit"));
        assertTrue(hasParameter(root.path("paths").path("/v1/conversations/{conversationId}/items").path("get"), "order"));
        assertTrue(hasParameter(root.path("paths").path("/v1/conversations/{conversationId}/items/{itemId}").path("get"), "conversationId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/conversations/{conversationId}/items/{itemId}").path("get"), "itemId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores").path("get"), "after"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores").path("get"), "limit"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores").path("get"), "order"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}").path("get"), "vectorStoreId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}").path("post"), "vectorStoreId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/search").path("post"), "vectorStoreId"));
        assertTrue(root.path("paths")
                .path("/v1/vector_stores/{vectorStoreId}/search")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("query"));
        assertTrue(root.path("paths")
                .path("/v1/vector_stores/{vectorStoreId}/search")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("max_num_results"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}").path("delete"), "vectorStoreId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/files").path("post"), "vectorStoreId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/files").path("get"), "after"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/files").path("get"), "limit"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/files").path("get"), "order"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/files").path("get"), "filter"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/files/{fileId}").path("get"), "vectorStoreId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/files/{fileId}").path("get"), "fileId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/files/{fileId}/content").path("get"), "fileId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/files/{fileId}").path("delete"), "fileId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/file_batches").path("post"), "vectorStoreId"));
        assertTrue(root.path("paths")
                .path("/v1/vector_stores/{vectorStoreId}/file_batches")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("files"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}").path("get"), "batchId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/cancel").path("post"), "batchId"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files").path("get"), "after"));
        assertTrue(hasParameter(root.path("paths").path("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files").path("get"), "filter"));
        assertTrue(hasParameter(root.path("paths").path("/v1/webhooks/openai").path("post"), "webhook-id"));
        assertTrue(hasParameter(root.path("paths").path("/v1/webhooks/openai").path("post"), "webhook-timestamp"));
        assertTrue(hasParameter(root.path("paths").path("/v1/webhooks/openai").path("post"), "webhook-signature"));
        assertTrue(root.path("paths")
                .path("/v1/webhooks/openai")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("type"));
        assertTrue(hasParameter(root.path("paths").path("/v1/models/{model}").path("get"), "model"));
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

    @Test
    void writeLatestOpenApiSnapshot() throws IOException {
        JsonNode latest = new com.prodigalgal.xaigateway.protocol.ingress.publicapi.PublicDocsBundleService().openApi();
        String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(latest);
        Files.writeString(Path.of("docs/openapi/public-openapi.json"), json);
    }
}
