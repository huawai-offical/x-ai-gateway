package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record OpsSloSummaryResponse(
        Instant observedAt,
        SummaryCards summary,
        List<SloPolicyResponse> activePolicies,
        List<RiskItem> risks,
        List<String> recommendedActions
) {

    public record SummaryCards(
            long requestCount,
            long failedRequestCount,
            double errorRate,
            double errorBudgetRatio,
            double errorBudgetRemainingRatio,
            double burnRate,
            String riskLevel,
            long silencedAlertCount
    ) {
    }

    public record RiskItem(
            String scopeType,
            String scopeRef,
            String policyName,
            double burnRate,
            double errorBudgetRemainingRatio,
            String riskLevel,
            List<String> suspectedCauses,
            List<String> suggestedActions
    ) {
    }
}
