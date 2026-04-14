package com.prodigalgal.xaigateway.gateway.core.routing;

import tools.jackson.databind.ObjectMapper;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisHealthStateStore implements HealthStateStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final GatewayProperties gatewayProperties;

    public RedisHealthStateStore(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            GatewayProperties gatewayProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.gatewayProperties = gatewayProperties;
    }

    @Override
    public Optional<CredentialHealthState> getCredentialState(Long credentialId) {
        String payload;
        try {
            payload = stringRedisTemplate.opsForValue().get(cacheKey(credentialId));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, CredentialHealthState.class));
        } catch (Exception exception) {
            stringRedisTemplate.delete(cacheKey(credentialId));
            return Optional.empty();
        }
    }

    @Override
    public void markCooldown(Long credentialId, String reason, Duration ttl) {
        Duration effectiveTtl = ttl == null ? gatewayProperties.getCache().getHealthCooldownTtl() : ttl;
        CredentialHealthState state = new CredentialHealthState(
                "COOLDOWN",
                reason,
                Instant.now().plus(effectiveTtl)
        );
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey(credentialId),
                    objectMapper.writeValueAsString(state),
                    effectiveTtl
            );
        } catch (RuntimeException exception) {
            // ignore in local mode
        } catch (Exception exception) {
            throw new IllegalStateException("无法写入 credential 冷却状态。", exception);
        }
    }

    @Override
    public void clear(Long credentialId) {
        try {
            stringRedisTemplate.delete(cacheKey(credentialId));
        } catch (RuntimeException exception) {
            // ignore in local mode
        }
    }

    private String cacheKey(Long credentialId) {
        return gatewayProperties.getCache().getKeyPrefix() + ":health:credential:" + credentialId;
    }
}
