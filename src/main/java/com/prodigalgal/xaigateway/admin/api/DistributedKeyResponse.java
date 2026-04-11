package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record DistributedKeyResponse(
        Long id,
        String keyName,
        String keyPrefix,
        String maskedKey,
        String description,
        boolean active,
        List<String> allowedProtocols,
        List<String> allowedModels,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
