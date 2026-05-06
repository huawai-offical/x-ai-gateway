package com.prodigalgal.xaigateway.admin.api;

public record MonitoringBillingBucketResponse(
        String bucket,
        long requestCount,
        long failedRequestCount,
        long usageRecordCount,
        long promptTokens,
        long completionTokens,
        long reasoningTokens,
        long totalTokens,
        long cacheHitTokens,
        long cacheWriteTokens,
        long savedInputTokens
) {
}
