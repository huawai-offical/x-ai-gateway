package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertTrue(response.providerPresets().stream().anyMatch(item ->
                "openai".equals(item.code())
                        && item.unsupportedFeatures().stream().anyMatch(feature -> feature.contains("non_core_official_apis_out_of_scope"))));
        assertTrue(response.providerPresets().stream().anyMatch(item ->
                "anthropic".equals(item.code())
                        && item.unsupportedFeatures().stream().anyMatch(feature -> feature.contains("non_core_provider_apis_out_of_scope"))));
        assertTrue(response.providerPresets().stream().anyMatch(item ->
                "gemini".equals(item.code())
                        && item.unsupportedFeatures().stream().anyMatch(feature -> feature.contains("non_core_provider_apis_out_of_scope"))));
        assertTrue(response.providerPresets().stream().anyMatch(item ->
                "vertex".equals(item.code())
                        && item.unsupportedFeatures().stream().anyMatch(feature -> feature.contains("non_core_provider_apis_out_of_scope"))));
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
        assertTrue(response.conformanceChecks().contains("openai.stored-chat-db-cursor-pagination"));
        assertTrue(response.conformanceChecks().contains("openai.idempotency-replay"));
        assertTrue(response.conformanceChecks().contains("openai.responses-local-lifecycle"));
        assertTrue(response.conformanceChecks().contains("openai.streaming-event-usage-sequence"));
        assertTrue(response.conformanceChecks().contains("openai.responses-stream-obfuscation"));
        assertTrue(response.conformanceChecks().contains("openai.responses-input-tokens-compact"));
        assertTrue(response.conformanceChecks().contains("openai.responses-input-tokens-native-passthrough"));
        assertTrue(response.conformanceChecks().contains("openai.responses-compact-native-passthrough"));
        assertTrue(response.conformanceChecks().contains("openai.responses-native-json-passthrough"));
        assertTrue(response.conformanceChecks().contains("openai.responses-native-stream-sse-passthrough"));
        assertTrue(response.conformanceChecks().contains("openai.responses-remote-lifecycle-passthrough"));
        assertTrue(response.conformanceChecks().contains("openai.responses-untracked-remote-lifecycle-route-hints"));
        assertTrue(response.conformanceChecks().contains("openai.responses-tool-registry-boundary"));
        assertTrue(response.conformanceChecks().contains("openai.conversations-local-lifecycle"));
        assertTrue(response.conformanceChecks().contains("openai.vector-stores-local-lifecycle"));
        assertTrue(response.conformanceChecks().contains("openai.vector-store-files-local-attachment"));
        assertTrue(response.conformanceChecks().contains("openai.vector-store-files-local-ingestion-artifact"));
        assertTrue(response.conformanceChecks().contains("openai.vector-store-file-content-local-read"));
        assertTrue(response.conformanceChecks().contains("openai.vector-store-search-local-text"));
        assertTrue(response.conformanceChecks().contains("openai.responses-file-search-local-vector-store-binding"));
        assertTrue(response.conformanceChecks().contains("openai.vector-store-file-batches-local-lifecycle"));
        assertFalse(response.conformanceChecks().contains("openai.batches-list-local-catalog"));
        assertFalse(response.conformanceChecks().contains("openai.models-delete-local-registry"));
        assertTrue(response.conformanceChecks().contains("openai.webhook-signature-replay"));
        assertTrue(response.conformanceChecks().contains("openai.webhooks-ingress-event-persistence"));
        assertTrue(response.conformanceChecks().contains("codex.responses-smoke-boundary"));
        assertFalse(response.compatibility().stream().anyMatch(item ->
                "openai".equals(item.protocol()) && item.supportedOperations().contains("models.registry_delete")));
        assertTrue(response.compatibility().stream().anyMatch(item ->
                "openai".equals(item.protocol()) && item.supportedOperations().contains("conversations.local_lineage")));
        assertTrue(response.compatibility().stream().anyMatch(item ->
                "openai".equals(item.protocol()) && item.supportedOperations().contains("vector_stores.local_lifecycle")));
        assertTrue(response.compatibility().stream().anyMatch(item ->
                "openai".equals(item.protocol()) && item.supportedOperations().contains("vector_store_files.local_attachment")));
        assertTrue(response.compatibility().stream().anyMatch(item ->
                "openai".equals(item.protocol()) && item.supportedOperations().contains("vector_store_files.local_ingestion_artifact")));
        assertTrue(response.compatibility().stream().anyMatch(item ->
                "openai".equals(item.protocol()) && item.supportedOperations().contains("vector_store_files.local_content_read")));
        assertTrue(response.compatibility().stream().anyMatch(item ->
                "openai".equals(item.protocol()) && item.supportedOperations().contains("vector_stores.local_text_search")));
        assertTrue(response.compatibility().stream().anyMatch(item ->
                "openai".equals(item.protocol()) && item.supportedOperations().contains("responses.file_search_local_vector_store_binding")));
        assertTrue(response.compatibility().stream().anyMatch(item ->
                "openai".equals(item.protocol()) && item.supportedOperations().contains("vector_store_file_batches.local_lifecycle")));
        assertTrue(response.compatibility().stream().anyMatch(item ->
                "openai".equals(item.protocol()) && item.supportedOperations().contains("webhooks.ingress_event_persistence")));
        assertTrue(response.routingNotes().stream().anyMatch(item -> item.contains("官方非核心 API 不纳入公开兼容面")));
        assertTrue(response.routingNotes().stream().anyMatch(item -> item.contains("file_search 可校验本地 vector_store_ids")));
        assertTrue(response.routingNotes().stream().anyMatch(item -> item.contains("OpenAI Conversations")));
        assertTrue(response.routingNotes().stream().anyMatch(item -> item.contains("OpenAI Vector Stores")));
        assertTrue(response.routingNotes().stream().anyMatch(item -> item.contains("/v1/webhooks/openai")));
        assertTrue(response.routingNotes().stream().anyMatch(item -> item.contains("Codex 单独限定为 Responses smoke")));
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
        assertTrue(openApi.path("paths").has("/v1/responses/input_tokens"));
        assertTrue(openApi.path("paths").has("/v1/responses/compact"));
        assertTrue(openApi.path("paths").has("/v1/responses/{responseId}/cancel"));
        assertTrue(openApi.path("paths").has("/v1/responses/{responseId}/input_items"));
        assertTrue(openApi.path("paths").has("/v1/conversations"));
        assertTrue(openApi.path("paths").has("/v1/conversations/{conversationId}"));
        assertTrue(openApi.path("paths").has("/v1/conversations/{conversationId}/items"));
        assertTrue(openApi.path("paths").has("/v1/conversations/{conversationId}/items/{itemId}"));
        assertTrue(openApi.path("paths").has("/v1/vector_stores"));
        assertTrue(openApi.path("paths").has("/v1/vector_stores/{vectorStoreId}"));
        assertTrue(openApi.path("paths").has("/v1/vector_stores/{vectorStoreId}/search"));
        assertTrue(openApi.path("paths").has("/v1/vector_stores/{vectorStoreId}/files"));
        assertTrue(openApi.path("paths").has("/v1/vector_stores/{vectorStoreId}/files/{fileId}"));
        assertTrue(openApi.path("paths").has("/v1/vector_stores/{vectorStoreId}/files/{fileId}/content"));
        assertTrue(openApi.path("paths").has("/v1/vector_stores/{vectorStoreId}/file_batches"));
        assertTrue(openApi.path("paths").has("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}"));
        assertTrue(openApi.path("paths").has("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/cancel"));
        assertTrue(openApi.path("paths").has("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files"));
        assertTrue(openApi.path("paths").has("/v1/webhooks/openai"));
        assertTrue(openApi.path("paths").has("/v1/models"));
        assertTrue(openApi.path("paths").has("/v1/models/{model}"));
        assertFalse(openApi.path("paths").path("/v1/models/{model}").has("delete"));
        assertTrue(openApi.path("paths").has("/v1/audio/translations"));
        assertTrue(openApi.path("paths").has("/v1/images/edits"));
        assertTrue(openApi.path("paths").has("/v1/images/variations"));
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
        var responsesProperties = openApi.path("paths")
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
        assertTrue(openApi.path("paths")
                .path("/v1/responses/input_tokens")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("input"));
        assertTrue(openApi.path("paths")
                .path("/v1/responses/compact")
                .path("post")
                .path("description")
                .asText()
                .contains("native upstream compaction"));
        assertTrue(openApi.path("paths")
                .path("/v1/responses/compact")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("input"));
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
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}").path("get"), "include"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}/cancel").path("post"), "responseId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "responseId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "after"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "include"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "limit"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/responses/{responseId}/input_items").path("get"), "order"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/conversations/{conversationId}").path("get"), "conversationId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/conversations/{conversationId}/items").path("post"), "conversationId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/conversations/{conversationId}/items").path("post"), "include"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/conversations/{conversationId}/items").path("get"), "after"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/conversations/{conversationId}/items").path("get"), "include"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/conversations/{conversationId}/items").path("get"), "limit"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/conversations/{conversationId}/items").path("get"), "order"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/conversations/{conversationId}/items/{itemId}").path("get"), "conversationId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/conversations/{conversationId}/items/{itemId}").path("get"), "itemId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores").path("get"), "after"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores").path("get"), "limit"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores").path("get"), "order"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}").path("get"), "vectorStoreId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}").path("post"), "vectorStoreId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/search").path("post"), "vectorStoreId"));
        assertTrue(openApi.path("paths")
                .path("/v1/vector_stores/{vectorStoreId}/search")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("query"));
        assertTrue(openApi.path("paths")
                .path("/v1/vector_stores/{vectorStoreId}/search")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("ranking_options"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}").path("delete"), "vectorStoreId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/files").path("post"), "vectorStoreId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/files").path("get"), "after"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/files").path("get"), "limit"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/files").path("get"), "order"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/files").path("get"), "filter"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/files/{fileId}").path("get"), "vectorStoreId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/files/{fileId}").path("get"), "fileId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/files/{fileId}/content").path("get"), "fileId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/files/{fileId}").path("delete"), "fileId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/file_batches").path("post"), "vectorStoreId"));
        assertTrue(openApi.path("paths")
                .path("/v1/vector_stores/{vectorStoreId}/file_batches")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("files"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}").path("get"), "batchId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/cancel").path("post"), "batchId"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files").path("get"), "after"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/vector_stores/{vectorStoreId}/file_batches/{batchId}/files").path("get"), "filter"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/webhooks/openai").path("post"), "webhook-id"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/webhooks/openai").path("post"), "webhook-timestamp"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/webhooks/openai").path("post"), "webhook-signature"));
        assertTrue(openApi.path("paths")
                .path("/v1/webhooks/openai")
                .path("post")
                .path("requestBody")
                .path("content")
                .path("application/json")
                .path("schema")
                .path("properties")
                .has("type"));
        assertTrue(hasParameter(openApi.path("paths").path("/v1/models/{model}").path("get"), "model"));
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
