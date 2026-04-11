package com.prodigalgal.xaigateway.gateway.core.cache;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class AffinityCacheService {

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

    public AffinityCacheService(
            StringRedisTemplate stringRedisTemplate,
            GatewayProperties gatewayProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.gatewayProperties = gatewayProperties;
    }

    public String getPrefixAffinity(Long distributedKeyId, String provider, String modelGroup, String prefixHash) {
        return stringRedisTemplate.opsForValue().get(prefixAffinityKey(distributedKeyId, provider, modelGroup, prefixHash));
    }

    public String getFingerprintAffinity(Long distributedKeyId, String provider, String modelGroup, String fingerprint) {
        return stringRedisTemplate.opsForValue().get(fingerprintAffinityKey(distributedKeyId, provider, modelGroup, fingerprint));
    }

    public String getModelAffinity(Long distributedKeyId, String provider, String modelGroup) {
        return stringRedisTemplate.opsForValue().get(modelAffinityKey(distributedKeyId, provider, modelGroup));
    }

    public void bindPrefixAffinity(Long distributedKeyId, String provider, String modelGroup, String prefixHash, Long credentialId) {
        upsert(prefixAffinityKey(distributedKeyId, provider, modelGroup, prefixHash), credentialId);
    }

    public void bindFingerprintAffinity(Long distributedKeyId, String provider, String modelGroup, String fingerprint, Long credentialId) {
        upsert(fingerprintAffinityKey(distributedKeyId, provider, modelGroup, fingerprint), credentialId);
    }

    public void bindModelAffinity(Long distributedKeyId, String provider, String modelGroup, Long credentialId) {
        upsert(modelAffinityKey(distributedKeyId, provider, modelGroup), credentialId);
    }

    public void invalidateIfMatches(AffinityBindingType type, Long distributedKeyId, String provider, String modelGroup, String hashOrNull, Long credentialId) {
        String key = switch (type) {
            case PREFIX -> prefixAffinityKey(distributedKeyId, provider, modelGroup, hashOrNull);
            case FINGERPRINT -> fingerprintAffinityKey(distributedKeyId, provider, modelGroup, hashOrNull);
            case MODEL_GROUP -> modelAffinityKey(distributedKeyId, provider, modelGroup);
        };

        stringRedisTemplate.execute(
                new DefaultRedisScript<>(INVALIDATE_IF_MATCH_SCRIPT, Long.class),
                List.of(key),
                String.valueOf(credentialId)
        );
    }

    private void upsert(String key, Long credentialId) {
        Duration ttl = gatewayProperties.getCache().getAffinityTtl();
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(UPSERT_SCRIPT, Long.class),
                List.of(key),
                String.valueOf(credentialId),
                String.valueOf(ttl.toSeconds())
        );
    }

    private String prefixAffinityKey(Long distributedKeyId, String provider, String modelGroup, String prefixHash) {
        return keyPrefix() + ":cache:prefix-affinity:" + distributedKeyId + ":" + provider + ":" + modelGroup + ":" + prefixHash;
    }

    private String fingerprintAffinityKey(Long distributedKeyId, String provider, String modelGroup, String fingerprint) {
        return keyPrefix() + ":cache:fingerprint-affinity:" + distributedKeyId + ":" + provider + ":" + modelGroup + ":" + fingerprint;
    }

    private String modelAffinityKey(Long distributedKeyId, String provider, String modelGroup) {
        return keyPrefix() + ":cache:model-affinity:" + distributedKeyId + ":" + provider + ":" + modelGroup;
    }

    private String keyPrefix() {
        return gatewayProperties.getCache().getKeyPrefix();
    }
}
