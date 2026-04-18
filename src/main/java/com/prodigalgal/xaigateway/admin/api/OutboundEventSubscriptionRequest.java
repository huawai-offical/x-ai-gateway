package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OutboundEventSubscriptionRequest(
        @NotBlank String subscriptionName,
        @NotNull Long channelId,
        String eventType,
        String severity,
        String entityType,
        String providerType,
        Long siteProfileId,
        Boolean enabled
) {
}
