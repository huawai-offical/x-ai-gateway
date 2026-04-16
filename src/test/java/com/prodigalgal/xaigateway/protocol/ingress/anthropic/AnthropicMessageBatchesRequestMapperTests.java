package com.prodigalgal.xaigateway.protocol.ingress.anthropic;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnthropicMessageBatchesRequestMapperTests {

    private final AnthropicMessageBatchesRequestMapper mapper =
            new AnthropicMessageBatchesRequestMapper(new ObjectMapper());

    @Test
    void shouldDeepCopyCreatePayloadAndExtractModel() throws Exception {
        ObjectNode request = (ObjectNode) new ObjectMapper().readTree("""
                {
                  "requests": [
                    {
                      "custom_id": "req-1",
                      "params": {
                        "model": "claude-sonnet-4",
                        "max_tokens": 256
                      }
                    }
                  ]
                }
                """);

        ObjectNode payload = mapper.toCreatePayload(request);

        assertNotSame(request, payload);
        assertEquals("claude-sonnet-4", mapper.extractModel(request));
        payload.put("extra", true);
        assertEquals(false, request.has("extra"));
    }

    @Test
    void shouldRejectNonObjectPayload() {
        assertThrows(IllegalArgumentException.class, () ->
                mapper.toCreatePayload(new ObjectMapper().createArrayNode()));
    }
}
