package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record OpsSummaryResponse(
        OpsTrafficSnapshotResponse snapshot,
        List<OpsAlertEventResponse> alerts,
        List<OpsScheduledProbeJobResponse> probeJobs,
        List<OpsOperationAuditResponse> recentLogs
) {
}
