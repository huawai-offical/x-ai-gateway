package com.prodigalgal.xaigateway.admin.api;

public record MonitoringBillingDimensionResponse(
        String dimension,
        String value,
        long requestCount,
        long failedRequestCount,
        long usageRecordCount,
        long promptTokens,
        long completionTokens,
        long reasoningTokens,
        long totalTokens,
        long cacheHitTokens,
        long cacheWriteTokens,
        long savedInputTokens,
        long averageDurationMs
) {
}
