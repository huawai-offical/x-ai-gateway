package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record TestDeliveryRequest(
        @NotNull Long channelId,
        String eventType,
        String severity,
        String entityType,
        String entityRef,
        String providerType,
        Long siteProfileId,
        String requestId,
        String gatewayResourceKey,
        String upstreamObjectId,
        String summary,
        Map<String, Object> details
) {
}
