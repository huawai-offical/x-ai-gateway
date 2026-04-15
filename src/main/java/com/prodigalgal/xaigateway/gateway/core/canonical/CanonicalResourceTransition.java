package com.prodigalgal.xaigateway.gateway.core.canonical;

import java.time.Instant;

public record CanonicalResourceTransition(
        String eventType,
        String status,
        Instant occurredAt
) {
}
