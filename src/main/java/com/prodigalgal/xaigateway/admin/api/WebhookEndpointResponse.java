package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record WebhookEndpointResponse(
        Long id,
        String endpointName,
        String endpointUrl,
        String signingMode,
        Integer timeoutMs,
        boolean enabled,
        String secretFingerprint,
        Instant createdAt,
        Instant updatedAt
) {
}
