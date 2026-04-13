package com.prodigalgal.xaigateway.gateway.core.cache;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisAffinityBindingStore implements AffinityBindingStore {

    private static final String UPSERT_SCRIPT = """
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            return 1
            """;

    private static final String INVALIDATE_IF_MATCH_SCRIPT = """
            local current = redis.call('GET', KEYS[1])
            if current == false then
              return 0
            end
            if current == ARGV[1] then
              redis.call('DEL', KEYS[1])
              return 1
            end
            return 0
            """;

    private final StringRedisTemplate stringRedisTemplate;

    public RedisAffinityBindingStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(UPSERT_SCRIPT, Long.class),
                List.of(key),
                value,
                String.valueOf(ttl.toSeconds())
        );
    }

    @Override
    public void invalidateIfMatches(String key, String expectedValue) {
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(INVALIDATE_IF_MATCH_SCRIPT, Long.class),
                List.of(key),
                expectedValue
        );
    }
}
