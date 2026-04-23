package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.credential.CredentialAuthKind;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CredentialResponse(
        Long id,
        String credentialName,
        ProviderType providerType,
        String baseUrl,
        CredentialAuthKind authKind,
        List<String> supportedModels,
        String secretFingerprint,
        Map<String, Object> credentialMetadata,
        boolean active,
        Instant cooldownUntil,
        String lastErrorCode,
        String lastErrorMessage,
        Instant lastErrorAt,
        Instant lastUsedAt,
        long totalRequestCount,
        long successfulRequestCount,
        long failedRequestCount,
        long canceledRequestCount,
        long totalTokenCount,
        long totalCacheHitTokenCount,
        long totalCacheWriteTokenCount,
        long totalSavedInputTokenCount,
        double requestSuccessRate,
        double cacheHitRate,
        long totalDurationMs,
        long durationSampleCount,
        double avgDurationMs,
        long totalFirstTokenMs,
        long firstTokenSampleCount,
        double avgFirstTokenMs,
        Long lastFirstTokenMs,
        Long minFirstTokenMs,
        Long maxFirstTokenMs,
        Long proxyId,
        Long tlsFingerprintProfileId,
        Long siteProfileId,
        Long poolId,
        String poolName,
        Instant createdAt,
        Instant updatedAt
) {
}
