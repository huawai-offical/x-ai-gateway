package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record DistributedKeyAccountGroupBindingResponse(
        Long id,
        Long distributedKeyId,
        Long groupId,
        ProviderType providerType,
        int priority,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
