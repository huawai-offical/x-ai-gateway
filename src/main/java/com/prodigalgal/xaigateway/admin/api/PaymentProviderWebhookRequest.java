package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;

public record PaymentProviderWebhookRequest(
        @NotBlank String provider,
        @NotBlank String payloadJson,
        @NotBlank String signatureHeader,
        @NotBlank String webhookSecret
) {
}
