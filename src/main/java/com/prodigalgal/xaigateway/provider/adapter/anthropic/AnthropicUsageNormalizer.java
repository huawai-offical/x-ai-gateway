package com.prodigalgal.xaigateway.provider.adapter.anthropic;

import com.prodigalgal.xaigateway.gateway.core.usage.GatewayUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;

@Service
public class AnthropicUsageNormalizer {

    public GatewayUsage normalize(Usage usage) {
        if (usage == null) {
            return GatewayUsage.empty();
        }

        Object nativeUsage = usage.getNativeUsage();
        GatewayUsage normalized = normalizeNativeUsage(nativeUsage);
        if (normalized != null) {
            return normalized;
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

    private GatewayUsage normalizeNativeUsage(Object usage) {
        Integer promptTokensValue = readInteger(usage, "inputTokens");
        Integer completionTokensValue = readInteger(usage, "outputTokens");
        Integer cacheWriteTokensValue = readInteger(usage, "cacheCreationInputTokens");
        Integer cacheHitTokensValue = readInteger(usage, "cacheReadInputTokens");
        if (promptTokensValue == null
                && completionTokensValue == null
                && cacheWriteTokensValue == null
                && cacheHitTokensValue == null) {
            return null;
        }

        int promptTokens = valueOrZero(promptTokensValue);
        int completionTokens = valueOrZero(completionTokensValue);
        int cacheWriteTokens = valueOrZero(cacheWriteTokensValue);
        int cacheHitTokens = valueOrZero(cacheHitTokensValue);
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

    private Integer readInteger(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            if (value instanceof Number number) {
                return number.intValue();
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
        return null;
    }
}
