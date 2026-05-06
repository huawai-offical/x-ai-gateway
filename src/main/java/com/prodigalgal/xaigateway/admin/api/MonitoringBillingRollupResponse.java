package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record MonitoringBillingRollupResponse(
        Instant from,
        Instant to,
        String period,
        long requestCount,
        long completedRequestCount,
        long failedRequestCount,
        double failureRate,
        long averageDurationMs,
        long usageRecordCount,
        long promptTokens,
        long completionTokens,
        long reasoningTokens,
        long totalTokens,
        long cacheHitTokens,
        long cacheWriteTokens,
        long savedInputTokens,
        MonitoringBillingSummaryResponse billing,
        List<MonitoringBillingBucketResponse> buckets,
        List<MonitoringBillingDimensionResponse> byProvider,
        List<MonitoringBillingDimensionResponse> byModel,
        List<MonitoringBillingDimensionResponse> byDistributedKey,
        List<MonitoringChannelHealthResponse> channelHealth
) {
}
