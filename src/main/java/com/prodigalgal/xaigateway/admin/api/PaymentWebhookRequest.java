package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;

public record PaymentWebhookRequest(
        @NotBlank String orderNo,
        String provider,
        @NotBlank String providerEventId,
        String providerTradeNo,
        String status,
        String payloadJson
) {
}
