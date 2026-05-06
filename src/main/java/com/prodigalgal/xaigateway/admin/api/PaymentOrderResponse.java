package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record PaymentOrderResponse(
        Long id,
        String orderNo,
        Long userId,
        String userEmail,
        String provider,
        long amountMinor,
        String currency,
        long tokenCredits,
        String status,
        String providerTradeNo,
        String providerInstanceCode,
        String checkoutUrl,
        String checkoutMethod,
        String providerPayloadJson,
        Instant checkoutExpiresAt,
        long refundAmountMinor,
        Instant refundedAt,
        Instant disputedAt,
        Instant reconciledAt,
        String reconcileStatus,
        long balanceAfterTokenCredits,
        boolean idempotentWebhook,
        String metadataJson,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt
) {
}
