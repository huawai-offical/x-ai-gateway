package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record CacheHitLogResponse(
        Long id,
        String requestId,
        Long distributedKeyId,
        String protocol,
        ProviderType providerType,
        Long credentialId,
        String modelGroup,
        String prefixHash,
        String fingerprint,
        String cacheKind,
        int cacheHitTokens,
        int cacheWriteTokens,
        int savedInputTokens,
        String cachedContentRef,
        Instant createdAt
) {
}
