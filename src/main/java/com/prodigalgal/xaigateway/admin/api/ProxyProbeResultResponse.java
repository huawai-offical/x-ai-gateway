package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ProxyProbeResultResponse(
        Long id,
        Long proxyId,
        String status,
        Long latencyMs,
        String targetHost,
        String errorMessage,
        Instant createdAt
) {
}
