package com.prodigalgal.xaigateway.gateway.core.routing;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class RedisHealthStateStoreTests {

    private final StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
    private final GatewayProperties gatewayProperties = new GatewayProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldWriteCooldownState() {
        RedisHealthStateStore store = new RedisHealthStateStore(stringRedisTemplate, objectMapper, gatewayProperties);

        store.markCooldown(101L, "status=503", Duration.ofMinutes(5));

        Mockito.verify(valueOperations).set(eq("xag:health:credential:101"), any(String.class), eq(Duration.ofMinutes(5)));
    }

    @Test
    void shouldReadCooldownState() throws Exception {
        RedisHealthStateStore store = new RedisHealthStateStore(stringRedisTemplate, objectMapper, gatewayProperties);
        CredentialHealthState state = new CredentialHealthState("COOLDOWN", "status=503", java.time.Instant.now().plusSeconds(300));
        Mockito.when(valueOperations.get("xag:health:credential:101"))
                .thenReturn(objectMapper.writeValueAsString(state));

        var result = store.getCredentialState(101L);

        assertTrue(result.isPresent());
        assertEquals("COOLDOWN", result.get().state());
    }

    @Test
    void shouldClearCooldownState() {
        RedisHealthStateStore store = new RedisHealthStateStore(stringRedisTemplate, objectMapper, gatewayProperties);

        store.clear(101L);

        Mockito.verify(stringRedisTemplate).delete("xag:health:credential:101");
    }
}
