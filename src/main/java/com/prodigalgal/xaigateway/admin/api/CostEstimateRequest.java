package com.prodigalgal.xaigateway.admin.api;

public record CostEstimateRequest(
        String providerType,
        String modelName,
        Long inputTokens,
        Long outputTokens,
        Long cacheHitTokens
) {
}
