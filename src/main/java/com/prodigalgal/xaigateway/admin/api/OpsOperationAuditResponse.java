package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record OpsOperationAuditResponse(
        Long id,
        String category,
        String action,
        String resourceType,
        String resourceRef,
        String detailJson,
        Instant createdAt
) {
}
