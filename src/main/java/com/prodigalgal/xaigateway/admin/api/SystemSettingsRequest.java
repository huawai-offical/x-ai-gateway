package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.Valid;

public record SystemSettingsRequest(
        @Valid
        UpstreamCacheSettingsRequest upstreamCache,
        @Valid
        UpstreamRuntimeSettingsRequest upstream
) {

    public record UpstreamCacheSettingsRequest(
            Boolean enabled,
            Boolean stickyByDistributedKey,
            Boolean prefixAffinityEnabled,
            Boolean fingerprintAffinityEnabled,
            String affinityTtl,
            Integer fingerprintMaxPrefixTokens,
            String keyPrefix
    ) {
    }

    public record UpstreamRuntimeSettingsRequest(
            Integer sdkTimeoutMs,
            Integer sdkStreamTimeoutMs,
            Integer httpTimeoutMs,
            Integer httpStreamTimeoutMs
    ) {
    }
}
