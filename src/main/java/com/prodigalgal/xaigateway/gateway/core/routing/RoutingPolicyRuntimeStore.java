package com.prodigalgal.xaigateway.gateway.core.routing;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoutingPolicyRuntimeStore {

    RoutingPolicyRateWindowState incrementRateWindow(
            String runtimeKey,
            Long policyId,
            String targetRef,
            Instant now,
            Duration window);

    Optional<RoutingPolicyCircuitState> findCircuitState(String runtimeKey);

    RoutingPolicyCircuitState markHalfOpen(String runtimeKey, Instant now);

    RoutingPolicyCircuitState recordSuccess(String runtimeKey, Long policyId, String targetRef, Instant now);

    RoutingPolicyCircuitState recordFailure(
            String runtimeKey,
            Long policyId,
            String targetRef,
            int failureThreshold,
            Duration openDuration,
            String reason,
            Instant now);

    List<RoutingPolicyRuntimeStoreSnapshot> snapshots(Instant now);

    void reset();

    void reset(String runtimeKey);
}

record RoutingPolicyRateWindowState(int counter, Instant expiresAt) {
}

record RoutingPolicyCircuitState(
        Long policyId,
        String targetRef,
        String state,
        int failureCount,
        Instant openUntil,
        String reason,
        Instant updatedAt
) {

    static RoutingPolicyCircuitState closed(Long policyId, String targetRef, Instant now) {
        return new RoutingPolicyCircuitState(policyId, targetRef, "CLOSED", 0, null, "success", now);
    }

    RoutingPolicyCircuitState halfOpen(Instant now) {
        return new RoutingPolicyCircuitState(policyId, targetRef, "HALF_OPEN", failureCount, openUntil, "cooldown-elapsed", now);
    }
}

record RoutingPolicyRuntimeStoreSnapshot(
        String runtimeKey,
        RoutingPolicyCircuitState circuitState,
        RoutingPolicyRateWindowState rateWindow
) {
}
