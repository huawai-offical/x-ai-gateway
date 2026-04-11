package com.prodigalgal.xaigateway.provider.adapter.gemini;

import com.prodigalgal.xaigateway.gateway.core.observability.GatewayObservabilityService;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class GeminiCachedContentReferenceService {

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
    private final GatewayProperties gatewayProperties;
    private final GatewayObservabilityService gatewayObservabilityService;

    public GeminiCachedContentReferenceService(
            StringRedisTemplate stringRedisTemplate,
            GatewayProperties gatewayProperties,
            GatewayObservabilityService gatewayObservabilityService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.gatewayProperties = gatewayProperties;
        this.gatewayObservabilityService = gatewayObservabilityService;
    }

    public Optional<GeminiCachedContentReference> find(Long distributedKeyId, String modelGroup, String prefixHash) {
        String raw = stringRedisTemplate.opsForValue().get(key(distributedKeyId, modelGroup, prefixHash));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        int delimiter = raw.indexOf('|');
        if (delimiter <= 0 || delimiter == raw.length() - 1) {
            return Optional.empty();
        }

        return Optional.of(new GeminiCachedContentReference(
                Long.parseLong(raw.substring(0, delimiter)),
                raw.substring(delimiter + 1)
        ));
    }

    public void bind(Long distributedKeyId, String modelGroup, String prefixHash, Long credentialId, String cachedContentName) {
        Duration ttl = gatewayProperties.getCache().getAffinityTtl();
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(UPSERT_SCRIPT, Long.class),
                List.of(key(distributedKeyId, modelGroup, prefixHash)),
                credentialId + "|" + cachedContentName,
                String.valueOf(ttl.toSeconds())
        );
        gatewayObservabilityService.recordUpstreamCacheReference(
                distributedKeyId,
                ProviderType.GEMINI_DIRECT,
                credentialId,
                modelGroup,
                prefixHash,
                cachedContentName,
                Instant.now().plus(ttl),
                "ACTIVE"
        );
    }

    public void invalidateIfMatches(Long distributedKeyId, String modelGroup, String prefixHash, Long credentialId, String cachedContentName) {
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(INVALIDATE_IF_MATCH_SCRIPT, Long.class),
                List.of(key(distributedKeyId, modelGroup, prefixHash)),
                credentialId + "|" + cachedContentName
        );
        gatewayObservabilityService.markUpstreamCacheReferenceInvalid(
                distributedKeyId,
                ProviderType.GEMINI_DIRECT,
                modelGroup,
                prefixHash
        );
    }

    private String key(Long distributedKeyId, String modelGroup, String prefixHash) {
        return gatewayProperties.getCache().getKeyPrefix()
                + ":cache:cached-content:"
                + distributedKeyId
                + ":gemini:"
                + modelGroup
                + ":"
                + prefixHash;
    }
}
