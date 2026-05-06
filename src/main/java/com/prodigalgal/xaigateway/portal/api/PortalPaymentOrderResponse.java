package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalPaymentOrderResponse(
        Long id,
        String orderNo,
        String provider,
        long amountMinor,
        String currency,
        long tokenCredits,
        String status,
        String providerTradeNo,
        String providerInstanceCode,
        String checkoutUrl,
        String checkoutMethod,
        Instant checkoutExpiresAt,
        long refundAmountMinor,
        Instant refundedAt,
        Instant disputedAt,
        Instant reconciledAt,
        String reconcileStatus,
        Instant paidAt,
        Instant createdAt
) {
}
