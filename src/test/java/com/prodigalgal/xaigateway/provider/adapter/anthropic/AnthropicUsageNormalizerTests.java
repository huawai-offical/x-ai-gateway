package com.prodigalgal.xaigateway.provider.adapter.anthropic;

import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.anthropic.api.AnthropicApi;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnthropicUsageNormalizerTests {

    @Test
    void shouldNormalizeCacheReadAndCacheWriteTokens() {
        AnthropicUsageNormalizer normalizer = new AnthropicUsageNormalizer();
        AnthropicApi.Usage usage = new AnthropicApi.Usage(900, 200, 120, 300);

        GatewayUsage normalized = normalizer.normalize(usage);

        assertEquals(1320, normalized.rawPromptTokens());
        assertEquals(900, normalized.promptTokens());
        assertEquals(300, normalized.cacheHitTokens());
        assertEquals(120, normalized.cacheWriteTokens());
        assertEquals(1520, normalized.totalTokens());
    }
}
