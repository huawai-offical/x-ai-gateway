package com.prodigalgal.xaigateway.admin.api;

import java.math.BigDecimal;
import java.time.Instant;

public record OpsAlertEventResponse(
        Long id,
        Long ruleId,
        String eventType,
        String severity,
        String title,
        String message,
        String status,
        String entityType,
        String entityRef,
        BigDecimal metricValue,
        Instant acknowledgedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
