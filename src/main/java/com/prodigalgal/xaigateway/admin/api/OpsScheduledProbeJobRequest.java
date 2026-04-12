package com.prodigalgal.xaigateway.admin.api;

public record OpsScheduledProbeJobRequest(
        String jobName,
        String probeType,
        String targetRef,
        Integer intervalSeconds,
        Boolean enabled
) {
}
