package com.prodigalgal.xaigateway.gateway.core.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisAuthCacheStore implements AuthCacheStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;

    public RedisAuthCacheStore(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Optional<DistributedKeyAuthSnapshot> get(String keyPrefix) {
        String payload;
        try {
            payload = stringRedisTemplate.opsForValue().get(cacheKey(keyPrefix));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, DistributedKeyAuthSnapshot.class));
        } catch (Exception exception) {
            stringRedisTemplate.delete(cacheKey(keyPrefix));
            return Optional.empty();
        }
    }

    @Override
    public void put(DistributedKeyAuthSnapshot snapshot, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey(snapshot.keyPrefix()),
                    objectMapper.writeValueAsString(snapshot),
                    ttl == null ? gatewayProperties.getCache().getAuthTtl() : ttl
            );
        } catch (RuntimeException exception) {
            // 本地或测试环境允许 Redis 不可用时退化为直查数据库。
        } catch (Exception exception) {
            throw new IllegalStateException("无法写入 DistributedKey 鉴权缓存。", exception);
        }
    }

    @Override
    public void invalidate(String keyPrefix) {
        try {
            stringRedisTemplate.delete(cacheKey(keyPrefix));
        } catch (RuntimeException exception) {
            // ignore in local mode
        }
    }

    private String cacheKey(String keyPrefix) {
        return gatewayProperties.getCache().getKeyPrefix() + ":auth:key:" + keyPrefix;
    }
}
