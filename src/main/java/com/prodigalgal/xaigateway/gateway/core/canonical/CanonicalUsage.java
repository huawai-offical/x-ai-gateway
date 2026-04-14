package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.response.GatewayUsageView;

public record CanonicalUsage(
        boolean present,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        int cacheHitTokens,
        int cacheWriteTokens,
        int reasoningTokens
) {

    public static CanonicalUsage empty() {
        return new CanonicalUsage(false, 0, 0, 0, 0, 0, 0);
    }

    public static CanonicalUsage from(GatewayUsageView usage) {
        if (usage == null || !usage.present()) {
            return empty();
        }
        return new CanonicalUsage(
                true,
                usage.promptTokens(),
                usage.completionTokens(),
                usage.totalTokens(),
                usage.cacheHitTokens(),
                usage.cacheWriteTokens(),
                usage.reasoningTokens()
        );
    }
}
