package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;
import java.util.List;

public record PortalKeyResponse(
        Long id,
        String keyName,
        String maskedKey,
        boolean active,
        List<String> allowedProtocols,
        List<String> allowedModels,
        Instant expiresAt,
        Integer rpmLimit,
        Integer tpmLimit,
        Integer concurrencyLimit,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
