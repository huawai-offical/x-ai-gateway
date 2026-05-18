package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayRequestFeatureServiceTests {

    private final GatewayRequestFeatureService service = new GatewayRequestFeatureService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDescribeChatCompletionsSemantics() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "gpt-4o");
        body.put("reasoning_effort", "high");
        body.putArray("tools").addObject().put("type", "function");
        body.putArray("messages")
                .addObject()
                .put("role", "user")
                .putArray("content")
                .addObject()
                .put("type", "image_url")
                .putObject("image_url")
                .put("url", "https://example.com/demo.png");

        GatewayRequestSemantics semantics = service.describe("/v1/chat/completions", body);

        assertEquals(TranslationResourceType.CHAT, semantics.resourceType());
        assertEquals(TranslationOperation.CHAT_COMPLETION, semantics.operation());
        assertEquals("chat.completions", semantics.surface());
        assertEquals("/v1/chat/completions", semantics.normalizedPath());
        assertTrue(semantics.requiresRouteSelection());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, semantics.routeSelectionMode());
        assertEquals(
                List.of(InteropFeature.CHAT_TEXT, InteropFeature.TOOLS, InteropFeature.REASONING, InteropFeature.IMAGE_INPUT),
                semantics.requiredFeatures()
        );
    }

    @Test
    void shouldDescribeResponsesSemanticsWithFileInput() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", "gpt-4o");
        body.putArray("input")
                .addObject()
                .put("type", "input_file")
                .put("file_id", "file-1");

        GatewayRequestSemantics semantics = service.describe("/v1/responses", body);

        assertEquals(TranslationResourceType.RESPONSE, semantics.resourceType());
        assertEquals(TranslationOperation.RESPONSE_CREATE, semantics.operation());
        assertEquals("responses", semantics.surface());
        assertEquals("/v1/responses", semantics.normalizedPath());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, semantics.routeSelectionMode());
        assertEquals(List.of(InteropFeature.RESPONSE_OBJECT, InteropFeature.FILE_INPUT), semantics.requiredFeatures());
    }

    @Test
    void shouldDescribeUploadFollowUpWithoutRouteSelection() {
        GatewayRequestSemantics semantics = service.describe("POST", "/v1/uploads/upload_1/parts", null);

        assertEquals(TranslationResourceType.UPLOAD, semantics.resourceType());
        assertEquals(TranslationOperation.UPLOAD_PART_ADD, semantics.operation());
        assertEquals("uploads", semantics.surface());
        assertEquals("/v1/uploads/{uploadId}/parts", semantics.normalizedPath());
        assertEquals(List.of(InteropFeature.UPLOAD_CREATE, InteropFeature.FILE_OBJECT), semantics.requiredFeatures());
        assertFalse(semantics.requiresRouteSelection());
        assertEquals(RouteSelectionMode.STORED_LINEAGE, semantics.routeSelectionMode());
    }

    @Test
    void shouldDescribeAnthropicMessagesSemantics() {
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("tools").addObject().put("name", "lookup_weather");
        body.putObject("thinking").put("type", "enabled");
        body.putArray("messages")
                .addObject()
                .put("role", "user")
                .putArray("content")
                .addObject()
                .put("type", "document")
                .putObject("source")
                .put("type", "url")
                .put("url", "https://example.com/doc.pdf");

        GatewayRequestSemantics semantics = service.describe("/v1/messages", body);

        assertEquals(TranslationResourceType.CHAT, semantics.resourceType());
        assertEquals(TranslationOperation.CHAT_COMPLETION, semantics.operation());
        assertEquals("messages", semantics.surface());
        assertEquals("/v1/messages", semantics.normalizedPath());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, semantics.routeSelectionMode());
        assertEquals(List.of(InteropFeature.CHAT_TEXT, InteropFeature.TOOLS, InteropFeature.REASONING, InteropFeature.FILE_INPUT), semantics.requiredFeatures());
    }

    @Test
    void shouldDescribeGeminiGenerateContentChatSemantics() {
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("tools").addObject().putArray("functionDeclarations").addObject().put("name", "lookup_weather");
        body.putObject("generationConfig").put("thinkingBudget", 128);
        body.putArray("contents")
                .addObject()
                .put("role", "user")
                .putArray("parts")
                .addObject()
                .putObject("fileData")
                .put("mimeType", "image/png")
                .put("fileUri", "https://example.com/demo.png");

        GatewayRequestSemantics semantics = service.describe("/v1beta/models/gemini-2.5-pro:generateContent", body);

        assertEquals(TranslationResourceType.CHAT, semantics.resourceType());
        assertEquals(TranslationOperation.CHAT_COMPLETION, semantics.operation());
        assertEquals("generateContent", semantics.surface());
        assertEquals("/v1beta/models/{model}:generateContent", semantics.normalizedPath());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, semantics.routeSelectionMode());
        assertEquals(List.of(InteropFeature.CHAT_TEXT, InteropFeature.TOOLS, InteropFeature.REASONING, InteropFeature.IMAGE_INPUT), semantics.requiredFeatures());
    }

    @Test
    void shouldDescribeGeminiGenerateContentImageResourceMode() {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("generationConfig")
                .putArray("responseModalities")
                .add("IMAGE");
        body.putArray("contents")
                .addObject()
                .put("role", "user")
                .putArray("parts")
                .addObject()
                .put("text", "draw a fox in watercolor");

        GatewayRequestSemantics semantics = service.describe("POST", "/v1beta/models/gemini-2.5-flash-image:generateContent", body);

        assertEquals(TranslationResourceType.IMAGE, semantics.resourceType());
        assertEquals(TranslationOperation.IMAGE_GENERATION, semantics.operation());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, semantics.routeSelectionMode());
        assertEquals(List.of(InteropFeature.IMAGE_GENERATION), semantics.requiredFeatures());
    }

    @Test
    void shouldDescribeGeminiGenerateContentAudioSpeechResourceMode() {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("generationConfig")
                .putArray("responseModalities")
                .add("AUDIO");
        body.putArray("contents")
                .addObject()
                .put("role", "user")
                .putArray("parts")
                .addObject()
                .put("text", "朗读这段欢迎词");

        GatewayRequestSemantics semantics = service.describe("POST", "/v1beta/models/gemini-2.5-flash-preview-tts:generateContent", body);

        assertEquals(TranslationResourceType.AUDIO, semantics.resourceType());
        assertEquals(TranslationOperation.AUDIO_SPEECH, semantics.operation());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, semantics.routeSelectionMode());
        assertEquals(List.of(InteropFeature.AUDIO_SPEECH), semantics.requiredFeatures());
    }

    @Test
    void shouldDescribeGoogleNativeEmbeddingsFilesAndBatchesWithSelectionModes() {
        GatewayRequestSemantics embedSemantics = service.describe("POST", "/v1beta/models/text-embedding-004:embedContent", null);
        GatewayRequestSemantics googleNamespaceEmbedSemantics = service.describe("POST", "/google/v1beta/models/text-embedding-004:embedContent", null);
        GatewayRequestSemantics batchEmbedSemantics = service.describe("POST", "/v1beta/models/text-embedding-004:batchEmbedContents", null);
        GatewayRequestSemantics googleUploadSemantics = service.describe("POST", "/google/upload/v1beta/files", null);
        GatewayRequestSemantics fileListSemantics = service.describe("GET", "/v1beta/files", null);
        GatewayRequestSemantics fileGetSemantics = service.describe("GET", "/v1beta/files/file_abc123", null);
        GatewayRequestSemantics batchCreateSemantics = service.describe("POST", "/v1beta/models/gemini-2.5-pro:batchGenerateContent", null);
        GatewayRequestSemantics batchGetSemantics = service.describe("GET", "/v1beta/batches/batch_abc123", null);
        GatewayRequestSemantics openAiBatchListSemantics = service.describe("GET", "/v1/batches", null);
        GatewayRequestSemantics tuningListSemantics = service.describe("GET", "/v1/fine_tuning/jobs", null);
        GatewayRequestSemantics tuningEventsSemantics = service.describe("GET", "/v1/fine_tuning/jobs/ftjob_1/events", null);
        GatewayRequestSemantics tuningCheckpointsSemantics = service.describe("GET", "/v1/fine_tuning/jobs/ftjob_1/checkpoints", null);

        assertEquals(TranslationOperation.EMBEDDING_CREATE, embedSemantics.operation());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, embedSemantics.routeSelectionMode());
        assertEquals(TranslationOperation.EMBEDDING_CREATE, googleNamespaceEmbedSemantics.operation());
        assertEquals("/v1beta/models/{model}:embedContent", googleNamespaceEmbedSemantics.normalizedPath());
        assertEquals(TranslationOperation.EMBEDDING_CREATE, batchEmbedSemantics.operation());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, batchEmbedSemantics.routeSelectionMode());
        assertEquals(TranslationOperation.FILE_CREATE, googleUploadSemantics.operation());
        assertEquals("/upload/v1beta/files", googleUploadSemantics.normalizedPath());
        assertEquals(TranslationOperation.FILE_LIST, fileListSemantics.operation());
        assertEquals(RouteSelectionMode.LOCAL_CATALOG, fileListSemantics.routeSelectionMode());
        assertEquals(TranslationOperation.FILE_GET, fileGetSemantics.operation());
        assertEquals(RouteSelectionMode.STORED_LINEAGE, fileGetSemantics.routeSelectionMode());
        assertEquals(TranslationOperation.BATCH_CREATE, batchCreateSemantics.operation());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, batchCreateSemantics.routeSelectionMode());
        assertEquals(TranslationOperation.BATCH_GET, batchGetSemantics.operation());
        assertEquals(RouteSelectionMode.STORED_LINEAGE, batchGetSemantics.routeSelectionMode());
        assertEquals(TranslationOperation.BATCH_LIST, openAiBatchListSemantics.operation());
        assertEquals(RouteSelectionMode.LOCAL_CATALOG, openAiBatchListSemantics.routeSelectionMode());
        assertEquals(TranslationOperation.TUNING_LIST, tuningListSemantics.operation());
        assertEquals(RouteSelectionMode.LOCAL_CATALOG, tuningListSemantics.routeSelectionMode());
        assertEquals(TranslationOperation.TUNING_EVENTS_LIST, tuningEventsSemantics.operation());
        assertEquals("/v1/fine_tuning/jobs/{jobId}/events", tuningEventsSemantics.normalizedPath());
        assertEquals(RouteSelectionMode.STORED_LINEAGE, tuningEventsSemantics.routeSelectionMode());
        assertEquals(TranslationOperation.TUNING_CHECKPOINTS_LIST, tuningCheckpointsSemantics.operation());
        assertEquals("/v1/fine_tuning/jobs/{jobId}/checkpoints", tuningCheckpointsSemantics.normalizedPath());
        assertEquals(RouteSelectionMode.STORED_LINEAGE, tuningCheckpointsSemantics.routeSelectionMode());
    }

    @Test
    void shouldNormalizeResourcePathsAndExtractPathParams() {
        assertEquals("/v1/files/{fileId}", service.normalizePath("/v1/files/file_1"));
        assertEquals("/v1/uploads/{uploadId}/parts", service.normalizePath("/v1/uploads/upload_1/parts"));
        assertEquals("/v1/batches/{batchId}/cancel", service.normalizePath("/v1/batches/batch_1/cancel"));
        assertEquals("/v1/fine_tuning/jobs/{jobId}", service.normalizePath("/v1/fine_tuning/jobs/ftjob_1"));
        assertEquals("/v1/fine_tuning/jobs/{jobId}/events", service.normalizePath("/v1/fine_tuning/jobs/ftjob_1/events"));
        assertEquals("/v1/fine_tuning/jobs/{jobId}/checkpoints", service.normalizePath("/v1/fine_tuning/jobs/ftjob_1/checkpoints"));
        assertEquals(
                "/v1beta/models/{model}:generateContent",
                service.normalizePath("/v1beta/models/gemini-2.5-pro:streamGenerateContent")
        );
        assertEquals("/v1beta/models/{model}:embedContent", service.normalizePath("/v1beta/models/text-embedding-004:embedContent"));
        assertEquals("/v1beta/models/{model}:batchEmbedContents", service.normalizePath("/v1beta/models/text-embedding-004:batchEmbedContents"));
        assertEquals("/v1beta/models/{model}:embedContent", service.normalizePath("/google/v1beta/models/text-embedding-004:embedContent"));
        assertEquals("/upload/v1beta/files", service.normalizePath("/google/upload/v1beta/files"));
        assertEquals("/v1beta/files/{fileName}", service.normalizePath("/v1beta/files/file_abc123"));
        assertEquals("/v1beta/batches/{batchName}:cancel", service.normalizePath("/v1beta/batches/batch_abc123:cancel"));

        assertEquals(java.util.Map.of("fileId", "file_1"), service.extractPathParams("/v1/files/file_1"));
        assertEquals(java.util.Map.of("uploadId", "upload_1"), service.extractPathParams("/v1/uploads/upload_1/parts"));
        assertEquals(java.util.Map.of("batchId", "batch_1"), service.extractPathParams("/v1/batches/batch_1/cancel"));
        assertEquals(java.util.Map.of("jobId", "ftjob_1"), service.extractPathParams("/v1/fine_tuning/jobs/ftjob_1"));
        assertEquals(java.util.Map.of("jobId", "ftjob_1"), service.extractPathParams("/v1/fine_tuning/jobs/ftjob_1/events"));
        assertEquals(java.util.Map.of("jobId", "ftjob_1"), service.extractPathParams("/v1/fine_tuning/jobs/ftjob_1/checkpoints"));
        assertEquals(
                java.util.Map.of("model", "gemini-2.5-pro"),
                service.extractPathParams("/v1beta/models/gemini-2.5-pro:generateContent")
        );
        assertEquals(
                java.util.Map.of("model", "text-embedding-004"),
                service.extractPathParams("/v1beta/models/text-embedding-004:embedContent")
        );
        assertEquals(
                java.util.Map.of("model", "text-embedding-004"),
                service.extractPathParams("/google/v1beta/models/text-embedding-004:embedContent")
        );
        assertEquals(
                java.util.Map.of("fileName", "file_abc123"),
                service.extractPathParams("/v1beta/files/file_abc123")
        );
        assertEquals(
                java.util.Map.of("batchName", "batch_abc123"),
                service.extractPathParams("/v1beta/batches/batch_abc123:cancel")
        );
    }

    @Test
    void shouldDescribeRealtimeClientSecretSemantics() {
        GatewayRequestSemantics semantics = service.describe("POST", "/v1/realtime/client_secrets", null);

        assertEquals(TranslationResourceType.REALTIME, semantics.resourceType());
        assertEquals(TranslationOperation.REALTIME_CLIENT_SECRET_CREATE, semantics.operation());
        assertEquals("realtime", semantics.surface());
        assertEquals("/v1/realtime/client_secrets", semantics.normalizedPath());
        assertEquals(RouteSelectionMode.DISTRIBUTED_TARGET, semantics.routeSelectionMode());
        assertEquals(List.of(InteropFeature.REALTIME_CLIENT_SECRET), semantics.requiredFeatures());
    }

    @Test
    void shouldDescribeExtendedNonChatResourceSemantics() {
        GatewayRequestSemantics rerank = service.describe("POST", "/v1/rerank", null);
        GatewayRequestSemantics videoCreate = service.describe("POST", "/v1/videos/generations", null);
        GatewayRequestSemantics videoGet = service.describe("GET", "/v1/videos/task_123", null);
        GatewayRequestSemantics musicCancel = service.describe("POST", "/v1/music/task_456/cancel", null);
        GatewayRequestSemantics taskGet = service.describe("GET", "/v1/tasks/task_789", null);
        GatewayRequestSemantics webSearch = service.describe("POST", "/v1/web_search", null);

        assertEquals(TranslationResourceType.RERANK, rerank.resourceType());
        assertEquals(TranslationOperation.RERANK_CREATE, rerank.operation());
        assertEquals(List.of(InteropFeature.RERANK), rerank.requiredFeatures());
        assertEquals(RouteSelectionMode.CATALOG_SELECTION, rerank.routeSelectionMode());

        assertEquals(TranslationResourceType.VIDEO, videoCreate.resourceType());
        assertEquals(TranslationOperation.VIDEO_GENERATION_CREATE, videoCreate.operation());
        assertEquals(List.of(InteropFeature.VIDEO_GENERATION, InteropFeature.ASYNC_TASK), videoCreate.requiredFeatures());

        assertEquals(TranslationOperation.VIDEO_GENERATION_GET, videoGet.operation());
        assertEquals("/v1/videos/{taskId}", videoGet.normalizedPath());
        assertEquals(RouteSelectionMode.STORED_LINEAGE, videoGet.routeSelectionMode());

        assertEquals(TranslationResourceType.MUSIC, musicCancel.resourceType());
        assertEquals(TranslationOperation.MUSIC_GENERATION_CANCEL, musicCancel.operation());
        assertEquals("/v1/music/{taskId}/cancel", musicCancel.normalizedPath());
        assertEquals(RouteSelectionMode.STORED_LINEAGE, musicCancel.routeSelectionMode());

        assertEquals(TranslationResourceType.TASK, taskGet.resourceType());
        assertEquals(TranslationOperation.TASK_GET, taskGet.operation());
        assertEquals(RouteSelectionMode.STORED_LINEAGE, taskGet.routeSelectionMode());

        assertEquals(TranslationResourceType.WEB_SEARCH, webSearch.resourceType());
        assertEquals(TranslationOperation.WEB_SEARCH_CREATE, webSearch.operation());
        assertEquals(List.of(InteropFeature.WEB_SEARCH), webSearch.requiredFeatures());
    }

    @Test
    void shouldNormalizeExtendedResourcePathsAndExtractTaskParams() {
        assertEquals("/v1/videos/generations", service.normalizePath("/v1/videos/generations"));
        assertEquals("/v1/videos/{taskId}", service.normalizePath("/v1/videos/task_123"));
        assertEquals("/v1/videos/{taskId}/cancel", service.normalizePath("/v1/videos/task_123/cancel"));
        assertEquals("/v1/music/generations", service.normalizePath("/v1/music/generations"));
        assertEquals("/v1/music/{taskId}", service.normalizePath("/v1/music/task_456"));
        assertEquals("/v1/music/{taskId}/cancel", service.normalizePath("/v1/music/task_456/cancel"));
        assertEquals("/v1/tasks/{taskId}", service.normalizePath("/v1/tasks/task_789"));
        assertEquals("/v1/tasks/{taskId}/cancel", service.normalizePath("/v1/tasks/task_789/cancel"));

        assertEquals(java.util.Map.of("taskId", "task_123"), service.extractPathParams("/v1/videos/task_123"));
        assertEquals(java.util.Map.of("taskId", "task_123"), service.extractPathParams("/v1/videos/task_123/cancel"));
        assertEquals(java.util.Map.of("taskId", "task_456"), service.extractPathParams("/v1/music/task_456"));
        assertEquals(java.util.Map.of("taskId", "task_789"), service.extractPathParams("/v1/tasks/task_789"));
    }
}
