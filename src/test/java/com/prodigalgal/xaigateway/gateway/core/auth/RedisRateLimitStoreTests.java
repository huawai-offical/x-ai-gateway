package com.prodigalgal.xaigateway.gateway.core.auth;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;

class RedisRateLimitStoreTests {

    private final StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void shouldReadCurrentCounter() {
        RedisRateLimitStore store = new RedisRateLimitStore(stringRedisTemplate);
        Mockito.when(valueOperations.get("xag:governance:rpm:1")).thenReturn("3");

        assertEquals(3L, store.get("xag:governance:rpm:1"));
    }

    @Test
    void shouldIncrementAndRefreshTtl() {
        RedisRateLimitStore store = new RedisRateLimitStore(stringRedisTemplate);
        Mockito.when(valueOperations.increment("xag:governance:rpm:1", 2L)).thenReturn(5L);

        assertEquals(5L, store.increment("xag:governance:rpm:1", 2L, Duration.ofSeconds(60)));
        Mockito.verify(stringRedisTemplate).expire("xag:governance:rpm:1", Duration.ofSeconds(60));
    }

    @Test
    void shouldDecrementCounter() {
        RedisRateLimitStore store = new RedisRateLimitStore(stringRedisTemplate);
        Mockito.when(valueOperations.decrement("xag:governance:concurrency:1")).thenReturn(1L);

        assertEquals(1L, store.decrement("xag:governance:concurrency:1"));
    }
}
