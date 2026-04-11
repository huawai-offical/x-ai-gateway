package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AccountPoolRequest(
        @NotBlank String poolName,
        @NotNull UpstreamAccountProviderType providerType,
        List<String> supportedModels,
        List<String> supportedProtocols,
        List<String> allowedClientFamilies,
        String description,
        Boolean active
) {
}
