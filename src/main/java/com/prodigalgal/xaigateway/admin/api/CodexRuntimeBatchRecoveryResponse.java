package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record CodexRuntimeBatchRecoveryResponse(
        String operation,
        Instant generatedAt,
        boolean dryRunOnly,
        boolean executed,
        boolean refreshQuota,
        Totals totals,
        List<CodexRuntimeBatchRecoveryItemResponse> items,
        Long auditEventId,
        String auditEventTitle
) {
    public record Totals(
            int total,
            int safe,
            int blocked,
            int alreadyReady,
            int executed,
            int failed,
            int skipped
    ) {
    }
}
