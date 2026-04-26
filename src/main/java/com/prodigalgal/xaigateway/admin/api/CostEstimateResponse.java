package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record CostEstimateResponse(
        String providerType,
        String modelName,
        String currency,
        long inputTokens,
        long outputTokens,
        long cacheHitTokens,
        long estimatedMicros,
        String estimatedDisplay,
        long inputTokenMicros,
        long outputTokenMicros,
        long cacheHitTokenMicros,
        Long distributedKeyId,
        String distributedKeyName,
        Long ownerUserId,
        Long budgetLimitMicros,
        Integer budgetWindowSeconds,
        Long singleRequestBudgetMicros,
        Long currentTokenCredits,
        boolean allowed,
        List<String> rejectionReasons
) {
}
