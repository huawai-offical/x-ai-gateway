package com.prodigalgal.xaigateway.gateway.core.routing;

import java.time.Instant;
import java.util.List;

public record RoutePolicyRuntimeDecision(
        boolean allowed,
        String reasonCode,
        String healthState,
        Instant retryAfter,
        List<Long> policyIds
) {
    public static RoutePolicyRuntimeDecision allow() {
        return new RoutePolicyRuntimeDecision(true, "allowed", "HEALTHY", null, List.of());
    }
}
