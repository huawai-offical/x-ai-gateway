package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "gateway.routing.runtime-store", name = "type", havingValue = "redis")
public class RedisRoutingPolicyRuntimeStore implements RoutingPolicyRuntimeStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisRoutingPolicyRuntimeStore.class);
    private static final Duration CIRCUIT_VISIBILITY_TTL_PADDING = Duration.ofHours(24);
    private static final Duration HALF_OPEN_PROBE_TTL = Duration.ofSeconds(5);

    private final StringRedisTemplate stringRedisTemplate;
    private final GatewayProperties gatewayProperties;
    private final InMemoryRoutingPolicyRuntimeStore fallbackStore = new InMemoryRoutingPolicyRuntimeStore();

    public RedisRoutingPolicyRuntimeStore(
            StringRedisTemplate stringRedisTemplate,
            GatewayProperties gatewayProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public RoutingPolicyRateWindowState incrementRateWindow(
            String runtimeKey,
            Long policyId,
            String targetRef,
            Instant now,
            Duration window) {
        try {
            String key = rateKey(runtimeKey);
            Long current = stringRedisTemplate.opsForValue().increment(key);
            if (current != null && current == 1L) {
                stringRedisTemplate.expire(key, window);
            }
            Long ttlMillis = stringRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            if (ttlMillis == null || ttlMillis <= 0) {
                stringRedisTemplate.expire(key, window);
                ttlMillis = window.toMillis();
            }
            return new RoutingPolicyRateWindowState(
                    current == null ? 0 : Math.toIntExact(current),
                    now.plusMillis(ttlMillis)
            );
        } catch (RuntimeException exception) {
            warnFallback(exception);
            return fallbackStore.incrementRateWindow(runtimeKey, policyId, targetRef, now, window);
        }
    }

    @Override
    public Optional<RoutingPolicyCircuitState> findCircuitState(String runtimeKey) {
        try {
            return toCircuitState(stringRedisTemplate.opsForHash().entries(circuitKey(runtimeKey)));
        } catch (RuntimeException exception) {
            warnFallback(exception);
            return fallbackStore.findCircuitState(runtimeKey);
        }
    }

    @Override
    public RoutingPolicyCircuitState markHalfOpen(String runtimeKey, Instant now) {
        try {
            Optional<RoutingPolicyCircuitState> existing = findCircuitState(runtimeKey);
            if (existing.isEmpty()) {
                return null;
            }
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                    halfOpenLockKey(runtimeKey),
                    now.toString(),
                    HALF_OPEN_PROBE_TTL
            );
            if (!Boolean.TRUE.equals(locked)) {
                return null;
            }
            RoutingPolicyCircuitState state = existing.get().halfOpen(now);
            writeCircuitState(runtimeKey, state, null);
            return state;
        } catch (RuntimeException exception) {
            warnFallback(exception);
            return fallbackStore.markHalfOpen(runtimeKey, now);
        }
    }

    @Override
    public RoutingPolicyCircuitState recordSuccess(String runtimeKey, Long policyId, String targetRef, Instant now) {
        try {
            RoutingPolicyCircuitState state = RoutingPolicyCircuitState.closed(policyId, targetRef, now);
            writeCircuitState(runtimeKey, state, null);
            stringRedisTemplate.delete(halfOpenLockKey(runtimeKey));
            return state;
        } catch (RuntimeException exception) {
            warnFallback(exception);
            return fallbackStore.recordSuccess(runtimeKey, policyId, targetRef, now);
        }
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
        try {
            String key = circuitKey(runtimeKey);
            Long nextFailureCount = stringRedisTemplate.opsForHash().increment(key, "failureCount", 1L);
            int failureCount = nextFailureCount == null ? 1 : Math.toIntExact(nextFailureCount);
            RoutingPolicyCircuitState existing = findCircuitState(runtimeKey).orElse(null);
            RoutingPolicyCircuitState state;
            if (failureCount >= failureThreshold) {
                state = new RoutingPolicyCircuitState(
                        policyId,
                        targetRef,
                        "OPEN",
                        failureCount,
                        now.plus(openDuration),
                        reason,
                        now
                );
                writeCircuitState(runtimeKey, state, openDuration.plus(CIRCUIT_VISIBILITY_TTL_PADDING));
                stringRedisTemplate.delete(halfOpenLockKey(runtimeKey));
            } else {
                state = new RoutingPolicyCircuitState(
                        policyId,
                        targetRef,
                        existing == null ? "CLOSED" : existing.state(),
                        failureCount,
                        existing == null ? null : existing.openUntil(),
                        reason,
                        now
                );
                writeCircuitState(runtimeKey, state, null);
                stringRedisTemplate.delete(halfOpenLockKey(runtimeKey));
            }
            return state;
        } catch (RuntimeException exception) {
            warnFallback(exception);
            return fallbackStore.recordFailure(runtimeKey, policyId, targetRef, failureThreshold, openDuration, reason, now);
        }
    }

    @Override
    public List<RoutingPolicyRuntimeStoreSnapshot> snapshots(Instant now) {
        try {
            Map<String, RoutingPolicyCircuitState> circuitStates = new HashMap<>();
            Map<String, RoutingPolicyRateWindowState> rateWindows = new HashMap<>();
            Set<String> keys = stringRedisTemplate.keys(prefix() + "*");
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }
            for (String key : keys) {
                if (key.startsWith(circuitPrefix())) {
                    String runtimeKey = key.substring(circuitPrefix().length());
                    toCircuitState(stringRedisTemplate.opsForHash().entries(key))
                            .ifPresent(state -> circuitStates.put(runtimeKey, state));
                    continue;
                }
                if (key.startsWith(ratePrefix())) {
                    String runtimeKey = key.substring(ratePrefix().length());
                    String rawCounter = stringRedisTemplate.opsForValue().get(key);
                    Long ttlMillis = stringRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
                    if (rawCounter == null || ttlMillis == null || ttlMillis <= 0) {
                        continue;
                    }
                    rateWindows.put(runtimeKey, new RoutingPolicyRateWindowState(
                            Integer.parseInt(rawCounter),
                            now.plusMillis(ttlMillis)
                    ));
                }
            }
            Map<String, RoutingPolicyRuntimeStoreSnapshot> snapshots = new HashMap<>();
            circuitStates.forEach((runtimeKey, circuitState) -> snapshots.put(
                    runtimeKey,
                    new RoutingPolicyRuntimeStoreSnapshot(runtimeKey, circuitState, rateWindows.get(runtimeKey))
            ));
            rateWindows.forEach((runtimeKey, rateWindow) -> snapshots.putIfAbsent(
                    runtimeKey,
                    new RoutingPolicyRuntimeStoreSnapshot(runtimeKey, null, rateWindow)
            ));
            return snapshots.values().stream()
                    .sorted(java.util.Comparator.comparing(RoutingPolicyRuntimeStoreSnapshot::runtimeKey))
                    .toList();
        } catch (RuntimeException exception) {
            warnFallback(exception);
            return fallbackStore.snapshots(now);
        }
    }

    @Override
    public void reset() {
        try {
            Set<String> keys = stringRedisTemplate.keys(prefix() + "*");
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
            fallbackStore.reset();
        } catch (RuntimeException exception) {
            warnFallback(exception);
            fallbackStore.reset();
        }
    }

    @Override
    public void reset(String runtimeKey) {
        if (runtimeKey == null || runtimeKey.isBlank()) {
            reset();
            return;
        }
        String normalized = runtimeKey.trim();
        try {
            stringRedisTemplate.delete(List.of(rateKey(normalized), circuitKey(normalized), halfOpenLockKey(normalized)));
            fallbackStore.reset(normalized);
        } catch (RuntimeException exception) {
            warnFallback(exception);
            fallbackStore.reset(normalized);
        }
    }

    private Optional<RoutingPolicyCircuitState> toCircuitState(Map<Object, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new RoutingPolicyCircuitState(
                longValue(raw.get("policyId")),
                stringValue(raw.get("targetRef")),
                defaultString(stringValue(raw.get("state")), "CLOSED"),
                intValue(raw.get("failureCount")),
                instantValue(raw.get("openUntil")),
                stringValue(raw.get("reason")),
                instantValue(raw.get("updatedAt"))
        ));
    }

    private void writeCircuitState(String runtimeKey, RoutingPolicyCircuitState state, Duration ttl) {
        String key = circuitKey(runtimeKey);
        Map<String, String> payload = new HashMap<>();
        payload.put("policyId", state.policyId() == null ? "" : state.policyId().toString());
        payload.put("targetRef", defaultString(state.targetRef(), ""));
        payload.put("state", defaultString(state.state(), "CLOSED"));
        payload.put("failureCount", String.valueOf(state.failureCount()));
        payload.put("openUntil", state.openUntil() == null ? "" : state.openUntil().toString());
        payload.put("reason", defaultString(state.reason(), ""));
        payload.put("updatedAt", state.updatedAt() == null ? "" : state.updatedAt().toString());
        stringRedisTemplate.opsForHash().putAll(key, payload);
        if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
            stringRedisTemplate.expire(key, ttl);
        }
    }

    private String rateKey(String runtimeKey) {
        return ratePrefix() + runtimeKey;
    }

    private String circuitKey(String runtimeKey) {
        return circuitPrefix() + runtimeKey;
    }

    private String halfOpenLockKey(String runtimeKey) {
        return prefix() + "half-open-lock:" + runtimeKey;
    }

    private String ratePrefix() {
        return prefix() + "rate:";
    }

    private String circuitPrefix() {
        return prefix() + "circuit:";
    }

    private String prefix() {
        String configured = gatewayProperties.getRouting().getRuntimeStore().getKeyPrefix();
        String value = configured == null || configured.isBlank()
                ? gatewayProperties.getCache().getKeyPrefix() + ":routing-runtime"
                : configured.trim();
        return value.endsWith(":") ? value : value + ":";
    }

    private void warnFallback(RuntimeException exception) {
        if (!gatewayProperties.getRouting().getRuntimeStore().isFallbackToMemory()) {
            throw exception;
        }
        logger.warn("Redis routing runtime store 暂不可用，已回退到本地内存运行态。", exception);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text.isBlank() ? null : text;
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Long longValue(Object value) {
        String text = stringValue(value);
        if (text == null) {
            return null;
        }
        return Long.parseLong(text);
    }

    private int intValue(Object value) {
        String text = stringValue(value);
        return text == null ? 0 : Integer.parseInt(text);
    }

    private Instant instantValue(Object value) {
        String text = stringValue(value);
        return text == null ? null : Instant.parse(text);
    }
}
