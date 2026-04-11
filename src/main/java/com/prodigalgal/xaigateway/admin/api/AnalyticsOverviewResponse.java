package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record AnalyticsOverviewResponse(
        Instant sampledFrom,
        Instant sampledTo,
        int bucketMinutes,
        long sampledRouteDecisionCount,
        long sampledCacheHitCount,
        long sampledActiveCacheReferenceCount,
        long totalCacheHitTokens,
        long totalCacheWriteTokens,
        long totalSavedInputTokens,
        List<BreakdownItem> providerBreakdown,
        List<BreakdownItem> protocolBreakdown,
        List<BreakdownItem> selectionSourceBreakdown,
        List<BreakdownItem> modelGroupBreakdown,
        List<TimelineBucket> timeline
) {

    public record BreakdownItem(
            String key,
            long count,
            long cacheHitTokens,
            long cacheWriteTokens,
            long savedInputTokens
    ) {
    }

    public record TimelineBucket(
            Instant bucketStart,
            long routeDecisionCount,
            long cacheHitCount,
            long cacheHitTokens,
            long cacheWriteTokens,
            long savedInputTokens
    ) {
    }
}
