package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record AccessGroupKeyGrantResponse(
        Long id,
        Long distributedKeyId,
        String keyName,
        String keyPrefix,
        String grantMode,
        boolean active,
        int priority,
        String reason,
        Instant createdAt,
        Instant updatedAt
) {
}
