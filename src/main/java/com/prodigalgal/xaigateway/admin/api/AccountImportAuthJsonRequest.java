package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record AccountImportAuthJsonRequest(
        Long groupId,
        String accountName,
        String externalAccountId,
        String accessToken,
        String refreshToken,
        String metadataJson,
        String authJsonContent,
        String authJsonFilePath,
        List<String> authJsonFilePaths,
        Boolean active,
        Long proxyId,
        Long tlsFingerprintProfileId,
        Long siteProfileId,
        List<String> supportedModels,
        Instant tokenExpiresAt,
        String refreshStatus,
        Instant nextRefreshAfter,
        Instant cooldownUntil,
        Instant quotaWindowStartedAt,
        Integer quotaWindowSeconds,
        Long quotaRemainingTokens,
        Long quotaRemainingRequests,
        String headerSnapshotJson,
        String lastRefreshResultJson
) {
}
