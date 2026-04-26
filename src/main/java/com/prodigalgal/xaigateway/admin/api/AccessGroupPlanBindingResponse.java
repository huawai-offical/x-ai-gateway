package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record AccessGroupPlanBindingResponse(
        Long id,
        Long planId,
        String planName,
        boolean active,
        int priority,
        Instant createdAt,
        Instant updatedAt
) {
}
