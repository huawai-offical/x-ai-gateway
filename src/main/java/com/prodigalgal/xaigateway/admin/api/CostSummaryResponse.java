package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record CostSummaryResponse(
        long totalModels,
        long activeModels,
        String currency,
        long sampleMonthlyMicros,
        String sampleMonthlyDisplay,
        List<CostEstimateResponse> modelDistribution
) {
}
