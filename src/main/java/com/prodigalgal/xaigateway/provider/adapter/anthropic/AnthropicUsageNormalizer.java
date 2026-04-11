package com.prodigalgal.xaigateway.provider.adapter.anthropic;

import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

@Service
public class AnthropicUsageNormalizer {

    public GatewayUsage normalize(Usage usage) {
        if (usage == null) {
            return GatewayUsage.empty();
        }

        Object nativeUsage = usage.getNativeUsage();
        if (nativeUsage instanceof AnthropicApi.Usage anthropicUsage) {
            return normalize(anthropicUsage);
        }

        int promptTokens = valueOrZero(usage.getPromptTokens());
        int completionTokens = valueOrZero(usage.getCompletionTokens());
        int totalTokens = usage.getTotalTokens() == null
                ? promptTokens + completionTokens
                : valueOrZero(usage.getTotalTokens());

        return new GatewayUsage(
                promptTokens,
                promptTokens,
                completionTokens,
                0,
                0,
                0,
                0,
                0,
                null,
                totalTokens,
                nativeUsage
        );
    }

    public GatewayUsage normalize(AnthropicApi.Usage usage) {
        if (usage == null) {
            return GatewayUsage.empty();
        }

        int promptTokens = valueOrZero(usage.inputTokens());
        int completionTokens = valueOrZero(usage.outputTokens());
        int cacheWriteTokens = valueOrZero(usage.cacheCreationInputTokens());
        int cacheHitTokens = valueOrZero(usage.cacheReadInputTokens());
        int rawPromptTokens = promptTokens + cacheWriteTokens + cacheHitTokens;
        int totalTokens = rawPromptTokens + completionTokens;

        return new GatewayUsage(
                rawPromptTokens,
                promptTokens,
                completionTokens,
                0,
                cacheHitTokens,
                cacheWriteTokens,
                cacheHitTokens,
                cacheWriteTokens,
                null,
                totalTokens,
                usage
        );
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
