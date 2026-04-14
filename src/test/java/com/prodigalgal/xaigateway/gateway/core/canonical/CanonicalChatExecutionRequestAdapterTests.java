package com.prodigalgal.xaigateway.gateway.core.canonical;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CanonicalChatExecutionRequestAdapterTests {

    private final CanonicalChatExecutionRequestAdapter adapter = new CanonicalChatExecutionRequestAdapter();

    @Test
    void shouldAdaptCanonicalRequestBackToChatExecutionRequest() {
        ObjectNode extensions = JsonNodeFactory.instance.objectNode().put("store", true);
        CanonicalRequest canonicalRequest = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.RESPONSES,
                "/v1/responses",
                "writer-fast",
                List.of(
                        new CanonicalMessage(CanonicalMessageRole.SYSTEM, List.of(CanonicalContentPart.text("you are helpful"))),
                        new CanonicalMessage(
                                CanonicalMessageRole.USER,
                                List.of(
                                        CanonicalContentPart.text("describe"),
                                        CanonicalContentPart.image("image/png", "https://example.com/cat.png", "cat.png"),
                                        CanonicalContentPart.file("application/pdf", "gateway://file-1", "doc.pdf")
                                )
                        ),
                        new CanonicalMessage(
                                CanonicalMessageRole.TOOL,
                                List.of(CanonicalContentPart.toolResult("call_1", "lookup_weather", "{\"city\":\"Shanghai\"}"))
                        )
                ),
                List.of(new CanonicalToolDefinition("lookup_weather", "Lookup weather", JsonNodeFactory.instance.objectNode().put("type", "object"), true)),
                JsonNodeFactory.instance.textNode("auto"),
                0.2,
                256,
                new CanonicalReasoningConfig(JsonNodeFactory.instance.objectNode().put("summary", "auto"), "high"),
                extensions
        );

        ChatExecutionRequest request = adapter.toExecutionRequest(canonicalRequest);

        assertEquals("responses", request.protocol());
        assertEquals("/v1/responses", request.requestPath());
        assertEquals(3, request.messages().size());
        assertEquals("system", request.messages().get(0).role());
        assertEquals("you are helpful", request.messages().get(0).content());
        assertEquals(2, request.messages().get(1).media().size());
        assertEquals("tool", request.messages().get(2).role());
        assertEquals("call_1", request.messages().get(2).toolCallId());
        assertEquals("lookup_weather", request.messages().get(2).toolName());
        assertNotNull(request.executionMetadata());
        assertEquals("high", request.executionMetadata().path("reasoning_effort").asText());
        assertEquals(true, request.executionMetadata().path("store").asBoolean());
    }
}
