package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalUsageItemResponse(
        String requestId,
        Long distributedKeyId,
        String protocol,
        String modelGroup,
        String providerType,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        String completeness,
        Instant createdAt
) {
}
