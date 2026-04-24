package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record LiveSessionEventResponse(
        Long id,
        String sessionKey,
        long eventId,
        String eventType,
        String direction,
        String payloadJson,
        long audioBytes,
        Instant createdAt
) {
}
