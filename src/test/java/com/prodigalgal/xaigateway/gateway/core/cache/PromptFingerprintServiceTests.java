package com.prodigalgal.xaigateway.gateway.core.cache;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PromptFingerprintServiceTests {

    @Test
    void shouldIgnoreVolatileFieldsWhenBuildingFingerprint() {
        GatewayProperties properties = new GatewayProperties();
        properties.getCache().setFingerprintMaxPrefixTokens(1024);
        PromptFingerprintService service = new PromptFingerprintService(new ObjectMapper(), properties);

        Map<String, Object> bodyA = Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hello")),
                "stream", true,
                "metadata", Map.of("traceId", "abc"),
                "user", "u-1"
        );
        Map<String, Object> bodyB = Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hello"))
        );

        String fingerprintA = service.buildFingerprint("openai", "/v1/chat/completions", bodyA);
        String fingerprintB = service.buildFingerprint("openai", "/v1/chat/completions", bodyB);

        assertEquals(fingerprintA, fingerprintB);
    }
}
