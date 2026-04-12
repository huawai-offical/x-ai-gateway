package com.prodigalgal.xaigateway.admin.api;

import java.math.BigDecimal;

public record OpsAlertRuleRequest(
        String ruleName,
        String metricKey,
        String comparisonOperator,
        BigDecimal thresholdValue,
        String severity,
        Boolean enabled,
        String description
) {
}
