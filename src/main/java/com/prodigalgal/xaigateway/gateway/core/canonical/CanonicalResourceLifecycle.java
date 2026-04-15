package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.resource.GatewayAsyncResourceType;
import java.time.Instant;

public record CanonicalResourceLifecycle(
        String resourceKey,
        GatewayAsyncResourceType resourceType,
        String status,
        String normalizedStatus,
        boolean terminal,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        int eventCount,
        CanonicalResourceTransition latestTransition,
        String failureReason,
        String cancelReason
) {
}
