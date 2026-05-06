package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisRoutingPolicyRuntimeStoreTests {

    @Test
    void shouldIncrementRateWindowWithRedisTtl() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(values);
        Mockito.when(values.increment("xag:routing-runtime:rate:policy:1:credential:7")).thenReturn(1L);
        Mockito.when(redisTemplate.getExpire("xag:routing-runtime:rate:policy:1:credential:7", TimeUnit.MILLISECONDS))
                .thenReturn(60_000L);
        RedisRoutingPolicyRuntimeStore store = new RedisRoutingPolicyRuntimeStore(redisTemplate, new GatewayProperties());
        Instant now = Instant.parse("2026-05-05T08:00:00Z");

        RoutingPolicyRateWindowState state = store.incrementRateWindow(
                "policy:1:credential:7",
                1L,
                "credential:7",
                now,
                Duration.ofMinutes(1)
        );

        assertEquals(1, state.counter());
        assertEquals(now.plusSeconds(60), state.expiresAt());
        Mockito.verify(redisTemplate).expire("xag:routing-runtime:rate:policy:1:credential:7", Duration.ofMinutes(1));
    }

    @Test
    void shouldResetRedisRuntimeKeysAndFallbackStore() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        Mockito.when(redisTemplate.keys("xag:routing-runtime:*")).thenReturn(Set.of(
                "xag:routing-runtime:rate:policy:1:credential:7",
                "xag:routing-runtime:circuit:policy:1:credential:7"
        ));
        RedisRoutingPolicyRuntimeStore store = new RedisRoutingPolicyRuntimeStore(redisTemplate, new GatewayProperties());

        store.reset();

        Mockito.verify(redisTemplate).delete(Set.of(
                "xag:routing-runtime:rate:policy:1:credential:7",
                "xag:routing-runtime:circuit:policy:1:credential:7"
        ));
    }

    @Test
    void shouldResetSingleRuntimeKey() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        RedisRoutingPolicyRuntimeStore store = new RedisRoutingPolicyRuntimeStore(redisTemplate, new GatewayProperties());

        store.reset("policy:1:credential:7");

        Mockito.verify(redisTemplate).delete(List.of(
                "xag:routing-runtime:rate:policy:1:credential:7",
                "xag:routing-runtime:circuit:policy:1:credential:7",
                "xag:routing-runtime:half-open-lock:policy:1:credential:7"
        ));
    }

    @Test
    void shouldFallbackToMemoryWhenRedisIsUnavailable() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        Mockito.when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        RedisRoutingPolicyRuntimeStore store = new RedisRoutingPolicyRuntimeStore(redisTemplate, new GatewayProperties());

        RoutingPolicyRateWindowState state = store.incrementRateWindow(
                "policy:1:credential:7",
                1L,
                "credential:7",
                Instant.parse("2026-05-05T08:00:00Z"),
                Duration.ofMinutes(1)
        );

        assertEquals(1, state.counter());
        assertTrue(state.expiresAt().isAfter(Instant.parse("2026-05-05T08:00:00Z")));
    }

    @Test
    void shouldUseHalfOpenProbeLock() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = Mockito.mock(ValueOperations.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = Mockito.mock(HashOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(values);
        Mockito.when(redisTemplate.opsForHash()).thenReturn(hashes);
        Mockito.when(hashes.entries("xag:routing-runtime:circuit:policy:1:credential:7"))
                .thenReturn(Map.of(
                        "policyId", "1",
                        "targetRef", "credential:7",
                        "state", "OPEN",
                        "failureCount", "3",
                        "openUntil", "2026-05-05T08:00:00Z",
                        "reason", "upstream 503",
                        "updatedAt", "2026-05-05T07:59:00Z"
                ));
        Mockito.when(values.setIfAbsent(
                        "xag:routing-runtime:half-open-lock:policy:1:credential:7",
                        "2026-05-05T08:01:00Z",
                        Duration.ofSeconds(5)))
                .thenReturn(false);
        RedisRoutingPolicyRuntimeStore store = new RedisRoutingPolicyRuntimeStore(redisTemplate, new GatewayProperties());

        RoutingPolicyCircuitState state = store.markHalfOpen(
                "policy:1:credential:7",
                Instant.parse("2026-05-05T08:01:00Z")
        );

        assertNull(state);
        Mockito.verify(hashes, Mockito.never()).putAll(Mockito.anyString(), Mockito.anyMap());
    }

}
