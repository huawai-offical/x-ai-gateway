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
        List<String> allowedProviderTypes,
        Instant expiresAt,
        Long budgetLimitMicros,
        Integer budgetWindowSeconds,
        Integer rpmLimit,
        Integer tpmLimit,
        Integer concurrencyLimit,
        Integer stickySessionTtlSeconds,
        List<String> allowedClientFamilies,
        boolean requireClientFamilyMatch,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
