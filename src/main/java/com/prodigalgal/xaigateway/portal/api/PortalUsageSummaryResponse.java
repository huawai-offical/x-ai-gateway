package com.prodigalgal.xaigateway.portal.api;

import java.util.List;

public record PortalUsageSummaryResponse(
        long requestCount,
        long totalTokens,
        long promptTokens,
        long completionTokens,
        long cacheHitTokens,
        List<PortalUsageItemResponse> recentUsage
) {
}
