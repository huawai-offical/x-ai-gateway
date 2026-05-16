package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicNativeGatewayChatRuntimeTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnthropicNativeGatewayChatRuntime runtime = new AnthropicNativeGatewayChatRuntime(null, null, null);

    @Test
    void shouldMapManagedAnthropicExtensionFields() throws Exception {
        var request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.ANTHROPIC_NATIVE,
                "/v1/messages",
                "claude-sonnet-4",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(),
                null,
                null,
                512,
                null,
                objectMapper.readTree("""
                        {
                          "service_tier":"auto",
                          "container":"session-1",
                          "metadata":{"user_id":"user-1","tenant":"community"},
                          "context_management":{"clear_function_results":true},
                          "mcp_servers":[{"type":"url","url":"https://mcp.example.com/sse","name":"docs"}],
                          "x_ai_gateway_anthropic_beta":"context-2025-10-01"
                        }
                        """)
        );

        var params = runtime.buildRequest(request, "claude-sonnet-4");

        assertEquals("auto", params.serviceTier().orElseThrow().asString());
        assertEquals("session-1", params.container().orElseThrow());
        assertEquals("user-1", params.metadata().orElseThrow().userId().orElseThrow());
        assertTrue(params.metadata().orElseThrow()._additionalProperties().containsKey("tenant"));
        assertTrue(params._additionalBodyProperties().containsKey("context_management"));
        assertTrue(params._additionalBodyProperties().containsKey("mcp_servers"));
        assertEquals(
                List.of("context-2025-10-01,mcp-client-2025-04-04"),
                params._additionalHeaders().values("anthropic-beta")
        );
    }
}
