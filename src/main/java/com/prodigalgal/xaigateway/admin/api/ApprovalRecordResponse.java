package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ApprovalRecordResponse(
        Long id,
        String decision,
        String actor,
        String reason,
        Instant decisionAt,
        Instant createdAt,
        Instant updatedAt
) {
}
