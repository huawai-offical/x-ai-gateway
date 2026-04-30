package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record CostSummaryResponse(
        long totalModels,
        long activeModels,
        String currency,
        long sampleMonthlyMicros,
        String sampleMonthlyDisplay,
        long settledRequestCount,
        long settledMicros,
        String settledDisplay,
        List<CostEstimateResponse> modelDistribution
) {
}
