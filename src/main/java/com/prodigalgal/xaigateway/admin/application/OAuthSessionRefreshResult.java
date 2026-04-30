package com.prodigalgal.xaigateway.admin.application;

import java.time.Instant;
import java.util.Map;

public record OAuthSessionRefreshResult(
        String adapterName,
        String accessToken,
        String refreshToken,
        Instant tokenExpiresAt,
        Instant nextRefreshAfter,
        Instant quotaWindowStartedAt,
        Integer quotaWindowSeconds,
        Long quotaRemainingTokens,
        Long quotaRemainingRequests,
        Map<String, String> headerSnapshot,
        Map<String, String> refreshMetadata
) {
}
