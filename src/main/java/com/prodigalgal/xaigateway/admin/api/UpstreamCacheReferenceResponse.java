package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record UpstreamCacheReferenceResponse(
        Long id,
        Long distributedKeyId,
        ProviderType providerType,
        Long credentialId,
        String modelGroup,
        String prefixHash,
        String externalCacheRef,
        String status,
        Instant expireAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
