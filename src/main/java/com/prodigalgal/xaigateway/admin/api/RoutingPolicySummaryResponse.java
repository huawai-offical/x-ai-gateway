package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record RoutingPolicySummaryResponse(
        int totalPolicies,
        int enabledPolicies,
        int retryConfigured,
        int fallbackConfigured,
        int circuitBreakerConfigured,
        int rateLimitConfigured,
        List<RouteGuardPolicyResponse> policies
) {
}
