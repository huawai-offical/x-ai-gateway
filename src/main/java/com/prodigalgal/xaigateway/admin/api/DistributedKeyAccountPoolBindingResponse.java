package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record DistributedKeyAccountPoolBindingResponse(
        Long id,
        Long distributedKeyId,
        Long poolId,
        ProviderType providerType,
        int priority,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
