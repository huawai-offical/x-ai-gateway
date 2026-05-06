package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record PaymentCheckoutResponse(
        PaymentOrderResponse order,
        String checkoutUrl,
        String checkoutMethod,
        String providerInstanceCode,
        String providerPayloadJson,
        Instant expiresAt
) {
}
