package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PaymentOrderCreateRequest(
        @NotNull Long userId,
        String provider,
        @Min(1) Long amountMinor,
        String currency,
        @Min(1) Long tokenCredits,
        String metadataJson
) {
}
