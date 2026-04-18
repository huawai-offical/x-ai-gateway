package com.prodigalgal.xaigateway.admin.application.operations;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedisLocalRuntimeSnapshotDriverTests {

    @Test
    void shouldCaptureAndRestoreSnapshot() {
        StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        Mockito.when(stringRedisTemplate.keys("xag:*")).thenReturn(Set.of("xag:key"));
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(valueOperations.get("xag:key")).thenReturn("value");
        Mockito.when(stringRedisTemplate.getExpire("xag:key")).thenReturn(30L);

        RedisLocalRuntimeSnapshotDriver driver = new RedisLocalRuntimeSnapshotDriver(stringRedisTemplate);
        RuntimeStateSnapshotDriver.RuntimeStateSnapshot snapshot = driver.capture("xag:");
        driver.restore(snapshot);

        assertEquals(1, snapshot.entries().size());
        Mockito.verify(valueOperations).set("xag:key", "value");
        Mockito.verify(stringRedisTemplate).expire("xag:key", Duration.ofSeconds(30));
    }
}
