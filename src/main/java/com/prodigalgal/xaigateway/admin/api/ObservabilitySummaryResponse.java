package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ObservabilitySummaryResponse(
        Instant sampledFrom,
        Instant sampledTo,
        int sampledRouteDecisionCount,
        int sampledCacheHitCount,
        int sampledActiveUpstreamCacheReferenceCount,
        int sampledUsageRecordCount,
        int sampledFinalUsageRecordCount,
        int sampledPartialUsageRecordCount,
        long totalCacheHitTokens,
        long totalCacheWriteTokens,
        long totalSavedInputTokens
) {
}
