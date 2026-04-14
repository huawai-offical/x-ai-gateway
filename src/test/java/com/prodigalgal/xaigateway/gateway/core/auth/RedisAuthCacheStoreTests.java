package com.prodigalgal.xaigateway.gateway.core.auth;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class RedisAuthCacheStoreTests {

    private final StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
    private final GatewayProperties gatewayProperties = new GatewayProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldReadCachedSnapshot() throws Exception {
        RedisAuthCacheStore store = new RedisAuthCacheStore(stringRedisTemplate, objectMapper, gatewayProperties);
        DistributedKeyAuthSnapshot snapshot = new DistributedKeyAuthSnapshot(1L, "sk-gw-test", "test", "masked", "hash", List.of("CODEX"));
        Mockito.when(valueOperations.get("xag:auth:key:sk-gw-test"))
                .thenReturn(objectMapper.writeValueAsString(snapshot));

        var result = store.get("sk-gw-test");

        assertTrue(result.isPresent());
        assertEquals("hash", result.get().secretHash());
    }

    @Test
    void shouldWriteCachedSnapshotWithTtl() {
        RedisAuthCacheStore store = new RedisAuthCacheStore(stringRedisTemplate, objectMapper, gatewayProperties);
        DistributedKeyAuthSnapshot snapshot = new DistributedKeyAuthSnapshot(1L, "sk-gw-test", "test", "masked", "hash", List.of("CODEX"));

        store.put(snapshot, Duration.ofMinutes(3));

        Mockito.verify(valueOperations).set(eq("xag:auth:key:sk-gw-test"), any(String.class), eq(Duration.ofMinutes(3)));
    }

    @Test
    void shouldInvalidateCachedSnapshot() {
        RedisAuthCacheStore store = new RedisAuthCacheStore(stringRedisTemplate, objectMapper, gatewayProperties);

        store.invalidate("sk-gw-test");

        Mockito.verify(stringRedisTemplate).delete("xag:auth:key:sk-gw-test");
    }
}
