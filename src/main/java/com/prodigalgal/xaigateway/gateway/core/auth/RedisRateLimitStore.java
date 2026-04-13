package com.prodigalgal.xaigateway.gateway.core.auth;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisRateLimitStore implements RateLimitStore {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisRateLimitStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public long get(String key) {
        try {
            String currentValue = stringRedisTemplate.opsForValue().get(key);
            return currentValue == null ? 0L : Long.parseLong(currentValue);
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    @Override
    public long increment(String key, long amount, Duration ttl) {
        if (amount <= 0) {
            return get(key);
        }
        try {
            Long current = stringRedisTemplate.opsForValue().increment(key, amount);
            if (ttl != null) {
                stringRedisTemplate.expire(key, ttl);
            }
            return current == null ? 0L : current;
        } catch (RuntimeException exception) {
            return amount;
        }
    }

    @Override
    public long decrement(String key) {
        try {
            Long current = stringRedisTemplate.opsForValue().decrement(key);
            return current == null ? 0L : Math.max(current, 0L);
        } catch (RuntimeException exception) {
            return 0L;
        }
    }
}
