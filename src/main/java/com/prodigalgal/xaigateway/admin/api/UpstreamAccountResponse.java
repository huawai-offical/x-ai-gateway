package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import java.time.Instant;
import java.util.List;

public record UpstreamAccountResponse(
        Long id,
        Long poolId,
        String accountName,
        UpstreamAccountProviderType providerType,
        List<String> supportedModels,
        String externalAccountId,
        boolean active,
        boolean frozen,
        boolean healthy,
        String lastErrorMessage,
        Long proxyId,
        Long tlsFingerprintProfileId,
        Instant lastRefreshAt,
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
        Instant createdAt,
        Instant updatedAt
) {
}
