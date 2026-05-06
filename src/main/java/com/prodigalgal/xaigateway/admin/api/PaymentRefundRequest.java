package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.Min;

public record PaymentRefundRequest(
        @Min(1) Long amountMinor,
        String reason,
        String payloadJson
) {
}
