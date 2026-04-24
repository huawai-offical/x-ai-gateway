package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record CostModelResponse(
        Long id,
        String providerType,
        String modelName,
        String currency,
        long inputTokenMicros,
        long outputTokenMicros,
        long cacheHitTokenMicros,
        boolean active,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
