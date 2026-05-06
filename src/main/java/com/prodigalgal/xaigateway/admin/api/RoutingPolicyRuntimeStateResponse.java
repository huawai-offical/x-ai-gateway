package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record RoutingPolicyRuntimeStateResponse(
        String runtimeKey,
        Long policyId,
        String targetRef,
        String state,
        int failureCount,
        Instant openUntil,
        int currentWindowCount,
        Instant windowExpiresAt,
        String reason
) {
}
