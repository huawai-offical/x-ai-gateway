package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record OpsProbeRunResponse(
        Long id,
        String probeName,
        String targetUrl,
        String status,
        String severity,
        String source,
        long latencyMs,
        Integer statusCode,
        String errorMessage,
        String detailJson,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {
}
