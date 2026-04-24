package com.prodigalgal.xaigateway.admin.api;

public record CostEstimateResponse(
        String providerType,
        String modelName,
        String currency,
        long inputTokens,
        long outputTokens,
        long cacheHitTokens,
        long estimatedMicros,
        String estimatedDisplay,
        long inputTokenMicros,
        long outputTokenMicros,
        long cacheHitTokenMicros
) {
}
