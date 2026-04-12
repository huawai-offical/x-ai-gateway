package com.prodigalgal.xaigateway.admin.api;

import java.math.BigDecimal;
import java.time.Instant;

public record OpsAlertRuleResponse(
        Long id,
        String ruleName,
        String metricKey,
        String comparisonOperator,
        BigDecimal thresholdValue,
        String severity,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
