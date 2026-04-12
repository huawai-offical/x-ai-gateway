package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record OpsTrafficSnapshotResponse(
        Instant observedAt,
        double qps,
        double errorRate,
        double p95LatencyMs,
        long providerFailures,
        long activeAlerts,
        List<String> affectedEntities
) {
}
