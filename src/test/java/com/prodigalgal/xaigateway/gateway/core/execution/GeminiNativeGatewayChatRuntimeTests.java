package com.prodigalgal.xaigateway.gateway.core.execution;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalContentPart;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalIngressProtocol;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessage;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalMessageRole;
import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiNativeGatewayChatRuntimeTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeminiNativeGatewayChatRuntime runtime = new GeminiNativeGatewayChatRuntime(null, null, null);

    @Test
    void shouldPreserveThinkingToolConfigAndGroundingTools() throws Exception {
        var request = new CanonicalRequest(
                "sk-gw-test",
                CanonicalIngressProtocol.GOOGLE_NATIVE,
                "/v1beta/models/gemini-2.5-pro:generateContent",
                "gemini-2.5-pro",
                List.of(new CanonicalMessage(CanonicalMessageRole.USER, List.of(CanonicalContentPart.text("hello")))),
                List.of(),
                null,
                null,
                null,
                null,
                objectMapper.readTree("""
                        {
                          "generationConfig": {
                            "thinkingConfig": {"thinkingBudget": 128, "includeThoughts": true},
                            "responseMimeType": "application/json"
                          },
                          "tools": [
                            {"googleSearch": {}},
                            {"urlContext": {}}
                          ],
                          "toolConfig": {
                            "functionCallingConfig": {
                              "mode": "ANY",
                              "allowedFunctionNames": ["lookup_weather"]
                            }
                          }
                        }
                        """)
        );

        String json = runtime.buildConfigForTests(request).toJson();

        assertTrue(json.contains("\"thinkingConfig\""));
        assertTrue(json.contains("\"thinkingBudget\":128"));
        assertTrue(json.contains("\"includeThoughts\":true"));
        assertTrue(json.contains("\"toolConfig\""));
        assertTrue(json.contains("\"functionCallingConfig\""));
        assertTrue(json.contains("\"googleSearch\""));
        assertTrue(json.contains("\"urlContext\""));
    }
}
