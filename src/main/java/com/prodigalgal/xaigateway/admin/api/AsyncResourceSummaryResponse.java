package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import java.time.Instant;

public record AsyncResourceSummaryResponse(
        String resourceKey,
        GatewayAsyncResourceType resourceType,
        String status,
        String normalizedStatus,
        String objectMode,
        String upstreamObjectId,
        int eventCount,
        Instant createdAt,
        Instant updatedAt
) {
}
