package com.prodigalgal.xaigateway.admin.application.integrations;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.Map;

public record PlatformEvent(
        String eventId,
        PlatformEventType eventType,
        Instant occurredAt,
        String severity,
        String category,
        String entityType,
        String entityRef,
        ProviderType providerType,
        Long siteProfileId,
        Long credentialId,
        Long accountId,
        String requestId,
        String gatewayResourceKey,
        String upstreamObjectId,
        String summary,
        Map<String, Object> details
) {
}
