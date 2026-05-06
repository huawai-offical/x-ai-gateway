package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record PaymentScheduledReconcileRunResponse(
        String runId,
        boolean scheduled,
        String provider,
        Instant from,
        Instant to,
        String status,
        long totalOrders,
        long anomalyOrders,
        Instant reconciledAt,
        PaymentReconcileReportResponse report
) {
}
