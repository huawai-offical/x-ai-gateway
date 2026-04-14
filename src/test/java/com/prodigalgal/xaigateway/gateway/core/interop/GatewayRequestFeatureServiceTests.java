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
        assertEquals(List.of(InteropFeature.RESPONSE_OBJECT, InteropFeature.FILE_INPUT), semantics.requiredFeatures());
    }

    @Test
    void shouldDescribeUploadFollowUpWithoutRouteSelection() {
        GatewayRequestSemantics semantics = service.describe("/v1/uploads/upload_1/parts", null);

        assertEquals(TranslationResourceType.UPLOAD, semantics.resourceType());
        assertEquals(TranslationOperation.UPLOAD_PART_ADD, semantics.operation());
        assertEquals(List.of(InteropFeature.UPLOAD_CREATE), semantics.requiredFeatures());
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
        assertEquals(List.of(InteropFeature.CHAT_TEXT, InteropFeature.TOOLS, InteropFeature.REASONING, InteropFeature.IMAGE_INPUT), semantics.requiredFeatures());
    }
}
