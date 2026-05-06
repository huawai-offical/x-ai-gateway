package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record PaymentReconcileRequest(
        String provider,
        Instant from,
        Instant to,
        String status
) {
}
