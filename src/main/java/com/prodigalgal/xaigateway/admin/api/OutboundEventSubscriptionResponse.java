package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record OutboundEventSubscriptionResponse(
        Long id,
        String subscriptionName,
        Long channelId,
        String eventType,
        String severity,
        String entityType,
        String providerType,
        Long siteProfileId,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
