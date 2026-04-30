package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.account.UpstreamAccountProviderType;

import java.time.Instant;

public record OAuthSessionRefreshRequest(
        Long accountId,
        String accountName,
        UpstreamAccountProviderType providerType,
        String accessToken,
        String refreshToken,
        String metadataJson,
        Instant now
) {
}
