package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record ObservabilityTraceResponse(
        RequestLogResponse requestLog,
        RouteDecisionLogResponse routeDecision,
        List<CacheHitLogResponse> cacheHits,
        List<UpstreamCacheReferenceResponse> upstreamCacheReferences,
        AsyncResourceSummaryResponse asyncResourceSummary,
        AsyncResourceDetailResponse asyncResourceDetail
) {
    public ObservabilityTraceResponse {
        cacheHits = cacheHits == null ? List.of() : List.copyOf(cacheHits);
        upstreamCacheReferences = upstreamCacheReferences == null ? List.of() : List.copyOf(upstreamCacheReferences);
    }
}
