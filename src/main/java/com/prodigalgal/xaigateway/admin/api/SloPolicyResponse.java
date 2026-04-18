package com.prodigalgal.xaigateway.admin.api;

import java.math.BigDecimal;
import java.time.Instant;

public record SloPolicyResponse(
        Long id,
        String policyName,
        String scopeType,
        String scopeRef,
        Integer windowMinutes,
        BigDecimal errorBudgetRatio,
        BigDecimal warningBurnRate,
        BigDecimal criticalBurnRate,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
