package com.prodigalgal.xaigateway.admin.api;

public record CostModelRequest(
        String providerType,
        String modelName,
        String currency,
        Long inputTokenMicros,
        Long outputTokenMicros,
        Long cacheHitTokenMicros,
        Boolean active,
        String notes
) {
}
