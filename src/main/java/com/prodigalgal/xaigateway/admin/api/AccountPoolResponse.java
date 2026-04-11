package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import java.time.Instant;
import java.util.List;

public record AccountPoolResponse(
        Long id,
        String poolName,
        UpstreamAccountProviderType providerType,
        List<String> supportedModels,
        List<String> supportedProtocols,
        List<String> allowedClientFamilies,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
