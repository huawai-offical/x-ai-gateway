package com.prodigalgal.xaigateway.gateway.core.interop;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(false, semantics.requiresRouteSelection());
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
        assertEquals(List.of(InteropFeature.CHAT_TEXT, InteropFeature.TOOLS, InteropFeature.REASONING, InteropFeature.FILE_INPUT), semantics.requiredFeatures());
    }

    @Test
    void shouldDescribeGeminiGenerateContentSemantics() {
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
        assertEquals(List.of(InteropFeature.CHAT_TEXT, InteropFeature.TOOLS, InteropFeature.REASONING, InteropFeature.IMAGE_INPUT), semantics.requiredFeatures());
    }

    @Test
    void shouldNormalizeResourcePathsAndExtractPathParams() {
        assertEquals("/v1/files/{fileId}", service.normalizePath("/v1/files/file_1"));
        assertEquals("/v1/uploads/{uploadId}/parts", service.normalizePath("/v1/uploads/upload_1/parts"));
        assertEquals("/v1/batches/{batchId}/cancel", service.normalizePath("/v1/batches/batch_1/cancel"));
        assertEquals("/v1/fine_tuning/jobs/{jobId}", service.normalizePath("/v1/fine_tuning/jobs/ftjob_1"));
        assertEquals(
                "/v1beta/models/{model}:generateContent",
                service.normalizePath("/v1beta/models/gemini-2.5-pro:streamGenerateContent")
        );

        assertEquals(java.util.Map.of("fileId", "file_1"), service.extractPathParams("/v1/files/file_1"));
        assertEquals(java.util.Map.of("uploadId", "upload_1"), service.extractPathParams("/v1/uploads/upload_1/parts"));
        assertEquals(java.util.Map.of("batchId", "batch_1"), service.extractPathParams("/v1/batches/batch_1/cancel"));
        assertEquals(java.util.Map.of("jobId", "ftjob_1"), service.extractPathParams("/v1/fine_tuning/jobs/ftjob_1"));
        assertEquals(
                java.util.Map.of("model", "gemini-2.5-pro"),
                service.extractPathParams("/v1beta/models/gemini-2.5-pro:generateContent")
        );
    }

    @Test
    void shouldDescribeRealtimeClientSecretSemantics() {
        GatewayRequestSemantics semantics = service.describe("POST", "/v1/realtime/client_secrets", null);

        assertEquals(TranslationResourceType.REALTIME, semantics.resourceType());
        assertEquals(TranslationOperation.REALTIME_CLIENT_SECRET_CREATE, semantics.operation());
        assertEquals("realtime", semantics.surface());
        assertEquals("/v1/realtime/client_secrets", semantics.normalizedPath());
        assertEquals(List.of(InteropFeature.REALTIME_CLIENT_SECRET), semantics.requiredFeatures());
    }
}
