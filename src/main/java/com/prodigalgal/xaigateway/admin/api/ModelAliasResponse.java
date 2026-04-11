package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record ModelAliasResponse(
        Long id,
        String aliasName,
        String aliasKey,
        boolean enabled,
        String description,
        List<ModelAliasRuleResponse> rules,
        Instant createdAt,
        Instant updatedAt
) {
}
