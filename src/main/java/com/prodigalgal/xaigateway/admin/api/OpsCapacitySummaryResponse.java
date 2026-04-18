package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record OpsCapacitySummaryResponse(
        Instant observedAt,
        List<DistributedKeyPressure> distributedKeys,
        List<AnalyticsOverviewResponse.BreakdownItem> providerRanking,
        List<AnalyticsOverviewResponse.BreakdownItem> modelGroupRanking,
        List<DashboardOverviewResponse.CredentialActivityItem> credentialRanking,
        List<DashboardOverviewResponse.DashboardAlert> alerts,
        List<String> recommendedActions
) {

    public record DistributedKeyPressure(
            Long distributedKeyId,
            String keyName,
            String maskedKey,
            String pressureLevel,
            Long budgetLimitMicros,
            Long currentBudgetMicros,
            Long remainingBudgetMicros,
            Integer rpmLimit,
            Long currentRpm,
            Long remainingRpm,
            Integer tpmLimit,
            Long currentTpm,
            Long remainingTpm,
            Integer concurrencyLimit,
            Long currentConcurrency,
            Long remainingConcurrency,
            List<String> notes
    ) {
    }
}
