package com.prodigalgal.xaigateway.gateway.core.routing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "gateway.routing.runtime-store", name = "type", havingValue = "memory", matchIfMissing = true)
public class InMemoryRoutingPolicyRuntimeStore implements RoutingPolicyRuntimeStore {

    private final Map<String, RateWindow> rateWindows = new ConcurrentHashMap<>();
    private final Map<String, RoutingPolicyCircuitState> circuitStates = new ConcurrentHashMap<>();

    @Override
    public RoutingPolicyRateWindowState incrementRateWindow(
            String runtimeKey,
            Long policyId,
            String targetRef,
            Instant now,
            Duration window) {
        RateWindow state = rateWindows.compute(runtimeKey, (ignored, existing) -> {
            if (existing == null || !existing.expiresAt().isAfter(now)) {
                return new RateWindow(new AtomicInteger(1), now.plus(window));
            }
            existing.counter().incrementAndGet();
            return existing;
        });
        return new RoutingPolicyRateWindowState(state.counter().get(), state.expiresAt());
    }

    @Override
    public Optional<RoutingPolicyCircuitState> findCircuitState(String runtimeKey) {
        return Optional.ofNullable(circuitStates.get(runtimeKey));
    }

    @Override
    public RoutingPolicyCircuitState markHalfOpen(String runtimeKey, Instant now) {
        return circuitStates.computeIfPresent(runtimeKey, (ignored, existing) -> existing.halfOpen(now));
    }

    @Override
    public RoutingPolicyCircuitState recordSuccess(String runtimeKey, Long policyId, String targetRef, Instant now) {
        RoutingPolicyCircuitState state = RoutingPolicyCircuitState.closed(policyId, targetRef, now);
        circuitStates.put(runtimeKey, state);
        return state;
    }

    @Override
    public RoutingPolicyCircuitState recordFailure(
            String runtimeKey,
            Long policyId,
            String targetRef,
            int failureThreshold,
            Duration openDuration,
            String reason,
            Instant now) {
        return circuitStates.compute(runtimeKey, (ignored, existing) -> {
            int nextFailureCount = existing == null ? 1 : existing.failureCount() + 1;
            if (nextFailureCount >= failureThreshold) {
                return new RoutingPolicyCircuitState(
                        policyId,
                        targetRef,
                        "OPEN",
                        nextFailureCount,
                        now.plus(openDuration),
                        reason,
                        now
                );
            }
            return new RoutingPolicyCircuitState(
                    policyId,
                    targetRef,
                    existing == null ? "CLOSED" : existing.state(),
                    nextFailureCount,
                    existing == null ? null : existing.openUntil(),
                    reason,
                    now
            );
        });
    }

    @Override
    public List<RoutingPolicyRuntimeStoreSnapshot> snapshots(Instant now) {
        List<RoutingPolicyRuntimeStoreSnapshot> snapshots = new ArrayList<>();
        circuitStates.forEach((runtimeKey, circuitState) -> snapshots.add(new RoutingPolicyRuntimeStoreSnapshot(
                runtimeKey,
                circuitState,
                rateWindow(runtimeKey, now)
        )));
        rateWindows.forEach((runtimeKey, rateWindow) -> {
            if (!rateWindow.expiresAt().isAfter(now) || circuitStates.containsKey(runtimeKey)) {
                return;
            }
            snapshots.add(new RoutingPolicyRuntimeStoreSnapshot(
                    runtimeKey,
                    null,
                    new RoutingPolicyRateWindowState(rateWindow.counter().get(), rateWindow.expiresAt())
            ));
        });
        return snapshots.stream()
                .sorted(Comparator.comparing(RoutingPolicyRuntimeStoreSnapshot::runtimeKey))
                .toList();
    }

    @Override
    public void reset() {
        rateWindows.clear();
        circuitStates.clear();
    }

    @Override
    public void reset(String runtimeKey) {
        if (runtimeKey == null || runtimeKey.isBlank()) {
            reset();
            return;
        }
        rateWindows.remove(runtimeKey.trim());
        circuitStates.remove(runtimeKey.trim());
    }

    private RoutingPolicyRateWindowState rateWindow(String runtimeKey, Instant now) {
        RateWindow rateWindow = rateWindows.get(runtimeKey);
        if (rateWindow == null || !rateWindow.expiresAt().isAfter(now)) {
            return null;
        }
        return new RoutingPolicyRateWindowState(rateWindow.counter().get(), rateWindow.expiresAt());
    }

    private record RateWindow(AtomicInteger counter, Instant expiresAt) {
    }
}
