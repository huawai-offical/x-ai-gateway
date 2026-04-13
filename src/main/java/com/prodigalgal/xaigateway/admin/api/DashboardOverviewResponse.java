package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record DashboardOverviewResponse(
        Instant sampledFrom,
        Instant sampledTo,
        int bucketMinutes,
        SummaryCards summary,
        List<AnalyticsOverviewResponse.BreakdownItem> providerRanking,
        List<AnalyticsOverviewResponse.BreakdownItem> protocolRanking,
        List<AnalyticsOverviewResponse.BreakdownItem> modelGroupRanking,
        List<AnalyticsOverviewResponse.BreakdownItem> selectionSourceRanking,
        List<AnalyticsOverviewResponse.BreakdownItem> cacheSourceRanking,
        List<AnalyticsOverviewResponse.CountBreakdownItem> usageCompletenessBreakdown,
        List<CredentialActivityItem> credentialRanking,
        List<DashboardAlert> alerts,
        List<AnalyticsOverviewResponse.TimelineBucket> timeline,
        List<RouteDecisionLogResponse> recentRouteDecisions,
        List<CacheHitLogResponse> recentCacheHits,
        List<UpstreamCacheReferenceResponse> activeUpstreamCacheReferences,
        List<UpstreamCacheReferenceResponse> expiringUpstreamCacheReferences
) {

    public record SummaryCards(
            long routeDecisionCount,
            long cacheHitCount,
            long activeCacheReferenceCount,
            long usageRecordCount,
            long finalUsageRecordCount,
            long partialUsageRecordCount,
            long totalCacheHitTokens,
            long totalCacheWriteTokens,
            long totalSavedInputTokens,
            double cacheHitRatio,
            double averageSavedInputTokensPerHit
    ) {
    }

    public record CredentialActivityItem(
            Long credentialId,
            String displayKey,
            String baseUrl,
            String providerType,
            long routeDecisionCount,
            long cacheHitCount,
            long cacheHitTokens,
            long cacheWriteTokens,
            long savedInputTokens
    ) {
    }

    public record DashboardAlert(
            String severity,
            String code,
            String title,
            String detail,
            List<String> affectedEntities,
            List<String> suspectedCauses,
            List<String> suggestedActions
    ) {
    }
}
