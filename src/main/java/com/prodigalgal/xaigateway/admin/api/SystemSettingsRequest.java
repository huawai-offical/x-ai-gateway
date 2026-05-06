package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.Valid;
import java.util.List;

public record SystemSettingsRequest(
        @Valid
        UpstreamCacheSettingsRequest upstreamCache,
        @Valid
        UpstreamRuntimeSettingsRequest upstream,
        @Valid
        SecuritySettingsRequest security
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

    public record SecuritySettingsRequest(
            Boolean ssrfProtectionEnabled,
            Boolean allowPrivateNetwork,
            List<String> allowedHosts,
            List<String> sensitiveWords
    ) {
    }
}
