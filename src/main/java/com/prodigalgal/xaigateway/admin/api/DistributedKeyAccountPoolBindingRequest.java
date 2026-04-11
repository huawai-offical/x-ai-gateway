package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import jakarta.validation.constraints.NotNull;

public record DistributedKeyAccountPoolBindingRequest(
        @NotNull Long distributedKeyId,
        @NotNull ProviderType providerType,
        Integer priority,
        Boolean active
) {
}
