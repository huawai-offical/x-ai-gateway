package com.prodigalgal.xaigateway.gateway.core.cache;

import com.prodigalgal.xaigateway.infra.config.GatewayProperties;
import org.springframework.stereotype.Service;

@Service
public class AffinityCacheService {

    private final AffinityBindingStore affinityBindingStore;
    private final GatewayProperties gatewayProperties;

    public AffinityCacheService(
            AffinityBindingStore affinityBindingStore,
            GatewayProperties gatewayProperties) {
        this.affinityBindingStore = affinityBindingStore;
        this.gatewayProperties = gatewayProperties;
    }

    public String getPrefixAffinity(Long distributedKeyId, String provider, String modelGroup, String prefixHash) {
        return affinityBindingStore.get(prefixAffinityKey(distributedKeyId, provider, modelGroup, prefixHash));
    }

    public String getFingerprintAffinity(Long distributedKeyId, String provider, String modelGroup, String fingerprint) {
        return affinityBindingStore.get(fingerprintAffinityKey(distributedKeyId, provider, modelGroup, fingerprint));
    }

    public String getModelAffinity(Long distributedKeyId, String provider, String modelGroup) {
        return affinityBindingStore.get(modelAffinityKey(distributedKeyId, provider, modelGroup));
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
        affinityBindingStore.invalidateIfMatches(key, String.valueOf(credentialId));
    }

    private void upsert(String key, Long credentialId) {
        affinityBindingStore.put(key, String.valueOf(credentialId), gatewayProperties.getCache().getAffinityTtl());
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
