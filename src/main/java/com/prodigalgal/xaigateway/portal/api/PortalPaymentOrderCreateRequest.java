package com.prodigalgal.xaigateway.portal.api;

import jakarta.validation.constraints.Min;

public record PortalPaymentOrderCreateRequest(
        String provider,
        @Min(1) Long amountMinor,
        String currency,
        @Min(1) Long tokenCredits,
        String metadataJson
) {
}
