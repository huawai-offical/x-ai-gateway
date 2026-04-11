package com.prodigalgal.xaigateway.provider.adapter.gemini;

import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.google.genai.metadata.GoogleGenAiUsage;
import org.springframework.stereotype.Service;

@Service
public class GeminiUsageNormalizer {

    public GatewayUsage normalize(Usage usage) {
        if (usage == null) {
            return GatewayUsage.empty();
        }

        Object nativeUsage = usage.getNativeUsage();
        if (usage instanceof GoogleGenAiUsage googleUsage) {
            return normalize(googleUsage);
        }
        if (nativeUsage instanceof com.google.genai.types.GenerateContentResponseUsageMetadata) {
            return normalize(GoogleGenAiUsage.from((com.google.genai.types.GenerateContentResponseUsageMetadata) nativeUsage));
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

    public GatewayUsage normalize(GoogleGenAiUsage usage) {
        if (usage == null) {
            return GatewayUsage.empty();
        }

        int rawPromptTokens = valueOrZero(usage.getPromptTokens());
        int completionTokens = valueOrZero(usage.getCompletionTokens());
        int cacheHitTokens = valueOrZero(usage.getCachedContentTokenCount());
        int promptTokens = Math.max(rawPromptTokens - cacheHitTokens, 0);
        int reasoningTokens = valueOrZero(usage.getThoughtsTokenCount());
        int totalTokens = valueOrZero(usage.getTotalTokens());

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
                totalTokens == 0 ? rawPromptTokens + completionTokens : totalTokens,
                usage.getNativeUsage()
        );
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
