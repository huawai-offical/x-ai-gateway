package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;
import java.time.Instant;

public record UpstreamAccountResponse(
        Long id,
        Long poolId,
        String accountName,
        UpstreamAccountProviderType providerType,
        String externalAccountId,
        boolean active,
        boolean frozen,
        boolean healthy,
        String lastErrorMessage,
        Long proxyId,
        Long tlsFingerprintProfileId,
        Instant lastRefreshAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
