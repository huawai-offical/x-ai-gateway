package com.prodigalgal.xaigateway.gateway.core.canonical;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prodigalgal.xaigateway.gateway.core.execution.ChatExecutionRequest;
import com.prodigalgal.xaigateway.gateway.core.execution.GatewayToolDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CanonicalChatMapperTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalChatMapper canonicalChatMapper = new CanonicalChatMapper(objectMapper);

    @Test
    void shouldMapChatExecutionRequestToCanonicalRequest() {
        ObjectNode reasoning = objectMapper.createObjectNode().put("summary", "auto");
        ObjectNode executionMetadata = objectMapper.createObjectNode()
                .set("reasoning", reasoning)
                .put("reasoning_effort", "high");
        ChatExecutionRequest request = new ChatExecutionRequest(
                "sk-gw-test",
                "openai",
                "/v1/chat/completions",
                "gpt-4o-mini",
                List.of(
                        new ChatExecutionRequest.MessageInput(
                                "system",
                                "you are helpful",
                                null,
                                null,
                                List.of()
                        ),
                        new ChatExecutionRequest.MessageInput(
                                "user",
                                "describe",
                                null,
                                null,
                                List.of(
                                        new ChatExecutionRequest.MediaInput("image", "image/png", "https://example.com/demo.png", "demo.png"),
                                        new ChatExecutionRequest.MediaInput("file", "application/pdf", "gateway://file-1", "doc.pdf")
                                )
                        ),
                        new ChatExecutionRequest.MessageInput(
                                "tool",
                                "{\"weather\":\"sunny\"}",
                                "call_1",
                                "lookup_weather",
                                List.of()
                        )
                ),
                List.of(new GatewayToolDefinition("lookup_weather", "Lookup weather", objectMapper.createObjectNode().put("type", "object"), true)),
                objectMapper.getNodeFactory().textNode("auto"),
                0.2,
                256,
                executionMetadata
        );

        CanonicalRequest canonicalRequest = canonicalChatMapper.toCanonicalRequest(request);

        assertEquals(CanonicalIngressProtocol.OPENAI, canonicalRequest.ingressProtocol());
        assertEquals("gpt-4o-mini", canonicalRequest.requestedModel());
        assertEquals(3, canonicalRequest.messages().size());
        assertEquals(CanonicalMessageRole.SYSTEM, canonicalRequest.messages().get(0).role());
        assertEquals(CanonicalPartType.TEXT, canonicalRequest.messages().get(0).parts().get(0).type());
        assertEquals(CanonicalMessageRole.USER, canonicalRequest.messages().get(1).role());
        assertEquals(3, canonicalRequest.messages().get(1).parts().size());
        assertEquals(CanonicalPartType.IMAGE, canonicalRequest.messages().get(1).parts().get(1).type());
        assertEquals(CanonicalPartType.FILE, canonicalRequest.messages().get(1).parts().get(2).type());
        assertEquals(CanonicalMessageRole.TOOL, canonicalRequest.messages().get(2).role());
        assertEquals(CanonicalPartType.TOOL_RESULT, canonicalRequest.messages().get(2).parts().get(0).type());
        assertEquals("call_1", canonicalRequest.messages().get(2).parts().get(0).toolCallId());
        assertEquals("lookup_weather", canonicalRequest.tools().get(0).name());
        assertNotNull(canonicalRequest.reasoning());
        assertEquals("high", canonicalRequest.reasoning().effort());
        assertEquals("auto", canonicalRequest.toolChoice().asText());
    }
}
