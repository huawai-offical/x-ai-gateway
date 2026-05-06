package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record PaymentReconcileReportResponse(
        String provider,
        Instant from,
        Instant to,
        String status,
        long totalOrders,
        long pendingOrders,
        long paidOrders,
        long failedOrders,
        long refundedOrders,
        long disputedOrders,
        long totalAmountMinor,
        long totalTokenCredits,
        Instant reconciledAt,
        List<PaymentOrderResponse> orders
) {
}
