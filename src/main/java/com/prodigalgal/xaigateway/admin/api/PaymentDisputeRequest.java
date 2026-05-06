package com.prodigalgal.xaigateway.admin.api;

public record PaymentDisputeRequest(
        String reason,
        String payloadJson
) {
}
