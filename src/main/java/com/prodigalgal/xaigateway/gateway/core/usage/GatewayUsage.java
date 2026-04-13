package com.prodigalgal.xaigateway.gateway.core.usage;

public record GatewayUsage(
        int rawPromptTokens,
        int promptTokens,
        int completionTokens,
        int reasoningTokens,
        int cacheHitTokens,
        int cacheWriteTokens,
        int upstreamCacheHitTokens,
        int upstreamCacheWriteTokens,
        String cachedContentRef,
        int totalTokens,
        Object nativeUsagePayload
) {

    public static GatewayUsage empty() {
        return new GatewayUsage(0, 0, 0, 0, 0, 0, 0, 0, null, 0, null);
    }

    public boolean isEmpty() {
        return rawPromptTokens == 0
                && promptTokens == 0
                && completionTokens == 0
                && reasoningTokens == 0
                && cacheHitTokens == 0
                && cacheWriteTokens == 0
                && upstreamCacheHitTokens == 0
                && upstreamCacheWriteTokens == 0
                && (cachedContentRef == null || cachedContentRef.isBlank())
                && totalTokens == 0
                && nativeUsagePayload == null;
    }

    public int savedInputTokens() {
        return Math.max(rawPromptTokens - promptTokens - cacheWriteTokens, 0);
    }
}
