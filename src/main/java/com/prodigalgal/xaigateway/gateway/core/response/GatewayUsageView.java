package com.prodigalgal.xaigateway.gateway.core.response;

public record GatewayUsageView(
        int rawPromptTokens,
        int promptTokens,
        int completionTokens,
        int reasoningTokens,
        int cacheHitTokens,
        int cacheWriteTokens,
        int upstreamCacheHitTokens,
        int upstreamCacheWriteTokens,
        int savedInputTokens,
        String cachedContentRef,
        int totalTokens,
        GatewayUsageCompleteness completeness,
        GatewayUsageSource source,
        Object nativeUsagePayload
) {

    public static GatewayUsageView empty() {
        return new GatewayUsageView(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                null,
                0,
                GatewayUsageCompleteness.NONE,
                GatewayUsageSource.NONE,
                null
        );
    }

    public boolean present() {
        return totalTokens > 0
                || rawPromptTokens > 0
                || promptTokens > 0
                || completionTokens > 0
                || reasoningTokens > 0
                || cacheHitTokens > 0
                || cacheWriteTokens > 0
                || upstreamCacheHitTokens > 0
                || upstreamCacheWriteTokens > 0
                || savedInputTokens > 0
                || (cachedContentRef != null && !cachedContentRef.isBlank())
                || nativeUsagePayload != null;
    }
}
