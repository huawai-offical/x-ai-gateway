package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record ModelAliasRuleResponse(
        Long id,
        String protocol,
        String targetModelName,
        String targetModelKey,
        ProviderType providerType,
        String baseUrlPattern,
        int priority,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
