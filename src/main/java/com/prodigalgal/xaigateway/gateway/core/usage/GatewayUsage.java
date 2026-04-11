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
}
