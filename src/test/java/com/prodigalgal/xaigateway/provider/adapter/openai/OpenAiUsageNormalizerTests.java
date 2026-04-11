package com.prodigalgal.xaigateway.provider.adapter.openai;

import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenAiUsageNormalizerTests {

    @Test
    void shouldNormalizeCachedTokensAndReasoningTokens() {
        OpenAiUsageNormalizer normalizer = new OpenAiUsageNormalizer();
        OpenAiApi.Usage usage = new OpenAiApi.Usage(
                400,
                1200,
                1600,
                new OpenAiApi.Usage.PromptTokensDetails(0, 300),
                new OpenAiApi.Usage.CompletionTokenDetails(50, 0, 0, 0)
        );

        GatewayUsage normalized = normalizer.normalize(usage);

        assertEquals(1200, normalized.rawPromptTokens());
        assertEquals(900, normalized.promptTokens());
        assertEquals(300, normalized.cacheHitTokens());
        assertEquals(50, normalized.reasoningTokens());
        assertEquals(1600, normalized.totalTokens());
    }
}
