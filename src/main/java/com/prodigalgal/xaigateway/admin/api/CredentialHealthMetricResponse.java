package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record CredentialHealthMetricResponse(
        Long credentialId,
        ProviderType providerType,
        String credentialLabel,
        String credentialPrefix,
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
