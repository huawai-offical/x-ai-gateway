package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record OutboundDeliveryResponse(
        Long id,
        String eventId,
        String eventType,
        Long channelId,
        String entityType,
        String entityRef,
        String requestId,
        String gatewayResourceKey,
        String upstreamObjectId,
        String deliveryStatus,
        int attemptCount,
        Instant nextRetryAt,
        String lastError,
        Integer responseCode,
        String responseSummary,
        String payloadJson,
        Instant occurredAt,
        Instant deliveredAt,
        Instant createdAt,
        Instant updatedAt
) {
}
