package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ReleaseRolloutStageResponse(
        Long id,
        String stage,
        String status,
        String message,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
