package com.prodigalgal.xaigateway.admin.api;

import java.math.BigDecimal;

public record SloPolicyRequest(
        String policyName,
        String scopeType,
        String scopeRef,
        Integer windowMinutes,
        BigDecimal errorBudgetRatio,
        BigDecimal warningBurnRate,
        BigDecimal criticalBurnRate,
        Boolean enabled,
        String description
) {
}
