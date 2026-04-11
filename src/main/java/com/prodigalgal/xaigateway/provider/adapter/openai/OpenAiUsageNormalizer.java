package com.prodigalgal.xaigateway.provider.adapter.openai;

import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

@Service
public class OpenAiUsageNormalizer {

    public GatewayUsage normalize(Usage usage) {
        if (usage == null) {
            return GatewayUsage.empty();
        }

        Object nativeUsage = usage.getNativeUsage();
        if (nativeUsage instanceof OpenAiApi.Usage openAiUsage) {
            return normalize(openAiUsage);
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

    public GatewayUsage normalize(OpenAiApi.Usage usage) {
        if (usage == null) {
            return GatewayUsage.empty();
        }

        int rawPromptTokens = valueOrZero(usage.promptTokens());
        int completionTokens = valueOrZero(usage.completionTokens());
        int cacheHitTokens = usage.promptTokensDetails() == null
                ? 0
                : valueOrZero(usage.promptTokensDetails().cachedTokens());
        int promptTokens = Math.max(rawPromptTokens - cacheHitTokens, 0);
        int reasoningTokens = usage.completionTokenDetails() == null
                ? 0
                : valueOrZero(usage.completionTokenDetails().reasoningTokens());
        int totalTokens = usage.totalTokens() == null
                ? rawPromptTokens + completionTokens
                : valueOrZero(usage.totalTokens());

        return new GatewayUsage(
                rawPromptTokens,
                promptTokens,
                completionTokens,
                reasoningTokens,
                cacheHitTokens,
                0,
                cacheHitTokens,
                0,
                null,
                totalTokens,
                usage
        );
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
