package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record OpsScheduledProbeJobResponse(
        Long id,
        String jobName,
        String probeType,
        String targetRef,
        int intervalSeconds,
        boolean enabled,
        Instant lastRunAt,
        String lastStatus,
        String lastErrorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
