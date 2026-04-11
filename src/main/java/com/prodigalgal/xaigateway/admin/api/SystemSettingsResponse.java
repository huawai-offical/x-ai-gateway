package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record SystemSettingsResponse(
        UpstreamCacheSettingsResponse upstreamCache,
        UpstreamRuntimeSettingsResponse upstream,
        Instant updatedAt
) {

    public record UpstreamCacheSettingsResponse(
            boolean enabled,
            boolean stickyByDistributedKey,
            boolean prefixAffinityEnabled,
            boolean fingerprintAffinityEnabled,
            String affinityTtl,
            int fingerprintMaxPrefixTokens,
            String keyPrefix
    ) {
    }

    public record UpstreamRuntimeSettingsResponse(
            int sdkTimeoutMs,
            int sdkStreamTimeoutMs,
            int httpTimeoutMs,
            int httpStreamTimeoutMs
    ) {
    }
}
