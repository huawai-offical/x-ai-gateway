package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record RoutingPolicyRuntimePlanResponse(
        int maxAttempts,
        boolean fallbackEnabled,
        List<String> fallbackOrder,
        boolean circuitBreakerEnabled,
        Integer circuitFailureThreshold,
        boolean rateLimitEnabled,
        Integer requestsPerMinute,
        List<Long> sourcePolicyIds,
        List<String> warnings
) {
}
