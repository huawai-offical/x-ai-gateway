package com.prodigalgal.xaigateway.gateway.core.model;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class ModelPolicyRuntimeStateService {

    private final Map<String, WindowCounter> requestWindows = new ConcurrentHashMap<>();

    public boolean requestRateAvailable(Long policyId, Long credentialId, String modelKey, int rpm) {
        if (policyId == null || credentialId == null || modelKey == null || rpm <= 0) {
            return true;
        }
        WindowCounter counter = requestWindows.get(key(policyId, credentialId, modelKey));
        return counter == null || counter.expired(Instant.now()) || counter.count() < rpm;
    }

    public void recordSuccess(Long policyId, Long credentialId, String modelKey) {
        if (policyId == null || credentialId == null || modelKey == null || modelKey.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        requestWindows.compute(key(policyId, credentialId, modelKey), (ignored, existing) -> {
            WindowCounter counter = existing == null || existing.expired(now)
                    ? new WindowCounter(now.plusSeconds(60), new AtomicLong())
                    : existing;
            counter.increment();
            return counter;
        });
    }

    public Optional<Long> currentRequestCount(Long policyId, Long credentialId, String modelKey) {
        if (policyId == null || credentialId == null || modelKey == null) {
            return Optional.empty();
        }
        WindowCounter counter = requestWindows.get(key(policyId, credentialId, modelKey));
        if (counter == null || counter.expired(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(counter.count());
    }

    public void reset() {
        requestWindows.clear();
    }

    private String key(Long policyId, Long credentialId, String modelKey) {
        return "model-policy:" + policyId + ":credential:" + credentialId + ":model:" + modelKey;
    }

    private record WindowCounter(Instant expiresAt, AtomicLong counter) {
        boolean expired(Instant now) {
            return expiresAt != null && !expiresAt.isAfter(now);
        }

        long count() {
            return counter.get();
        }

        void increment() {
            counter.incrementAndGet();
        }
    }
}
