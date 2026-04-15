package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalResourceTransition;
import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import java.time.Instant;

public record AsyncResourceSummaryResponse(
        String resourceKey,
        GatewayAsyncResourceType resourceType,
        String status,
        String normalizedStatus,
        boolean terminal,
        boolean deleted,
        String objectMode,
        String upstreamObjectId,
        int eventCount,
        CanonicalResourceTransition latestTransition,
        String failureReason,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt
) {
}
