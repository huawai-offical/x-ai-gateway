package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record HealthMetricResponse(
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        long canceledRequests,
        double successRate,
        double availabilityRate,
        double errorRate,
        double cancellationRate,
        double avgDurationMs,
        Instant lastSuccessfulAt,
        Instant lastFailedAt
) {
}
