package com.prodigalgal.xaigateway.provider.adapter.gemini;

import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.google.genai.metadata.GoogleGenAiTrafficType;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeminiUsageNormalizerTests {

    @Test
    void shouldNormalizeCachedContentAndThoughtTokens() {
        GeminiUsageNormalizer normalizer = new GeminiUsageNormalizer();
        GoogleGenAiUsage usage = new GoogleGenAiUsage(
                1000,
                350,
                1350,
                40,
                280,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                GoogleGenAiTrafficType.ON_DEMAND,
                null
        );

        GatewayUsage normalized = normalizer.normalize(usage);

        assertEquals(1000, normalized.rawPromptTokens());
        assertEquals(720, normalized.promptTokens());
        assertEquals(280, normalized.cacheHitTokens());
        assertEquals(40, normalized.reasoningTokens());
        assertEquals(1350, normalized.totalTokens());
    }
}
