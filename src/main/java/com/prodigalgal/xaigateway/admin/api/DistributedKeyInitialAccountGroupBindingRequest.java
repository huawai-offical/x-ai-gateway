package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import jakarta.validation.constraints.NotNull;

public record DistributedKeyInitialAccountGroupBindingRequest(
        @NotNull Long groupId,
        @NotNull ProviderType providerType,
        Integer priority,
        Boolean active
) {
}
