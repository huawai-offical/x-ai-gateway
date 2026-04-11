package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ProxyResponse(
        Long id,
        String proxyName,
        String proxyUrl,
        boolean active,
        String lastStatus,
        Long lastLatencyMs,
        String lastErrorMessage,
        Instant lastProbedAt,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
