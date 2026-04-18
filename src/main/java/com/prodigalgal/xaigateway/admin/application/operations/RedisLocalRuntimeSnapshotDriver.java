package com.prodigalgal.xaigateway.admin.application.operations;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class RedisLocalRuntimeSnapshotDriver implements RuntimeStateSnapshotDriver {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisLocalRuntimeSnapshotDriver(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public RuntimeStateSnapshot capture(String keyPrefix) {
        Set<String> keys = stringRedisTemplate.keys(keyPrefix + "*");
        List<RuntimeStateEntry> entries = keys == null
                ? List.of()
                : keys.stream()
                .sorted(Comparator.naturalOrder())
                .map(this::toEntry)
                .toList();
        return new RuntimeStateSnapshot(keyPrefix, entries);
    }

    @Override
    public void restore(RuntimeStateSnapshot snapshot) {
        if (snapshot == null || snapshot.entries().isEmpty()) {
            return;
        }
        snapshot.entries().forEach(entry -> {
            stringRedisTemplate.opsForValue().set(entry.key(), entry.value() == null ? "" : entry.value());
            if (entry.ttlSeconds() != null && entry.ttlSeconds() > 0) {
                stringRedisTemplate.expire(entry.key(), Duration.ofSeconds(entry.ttlSeconds()));
            }
        });
    }

    @Override
    public VerificationResult verify(RuntimeStateSnapshot snapshot) {
        if (snapshot == null) {
            return new VerificationResult(false, "缺少 runtime state snapshot。");
        }
        return new VerificationResult(true, "Redis runtime state snapshot 可用。");
    }

    private RuntimeStateEntry toEntry(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        Long ttl = stringRedisTemplate.getExpire(key);
        return new RuntimeStateEntry(key, value, ttl);
    }
}
