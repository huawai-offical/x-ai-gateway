package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.portal.application.PortalSocialOAuthSettingsView;
import java.time.Instant;
import java.util.List;

public record SystemSettingsResponse(
        UpstreamCacheSettingsResponse upstreamCache,
        UpstreamRuntimeSettingsResponse upstream,
        SecuritySettingsResponse security,
        PortalSocialOAuthSettingsView socialOAuth,
        Instant updatedAt
) {
    public SystemSettingsResponse(
            UpstreamCacheSettingsResponse upstreamCache,
            UpstreamRuntimeSettingsResponse upstream,
            SecuritySettingsResponse security,
            Instant updatedAt) {
        this(upstreamCache, upstream, security, null, updatedAt);
    }

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

    public record SecuritySettingsResponse(
            boolean ssrfProtectionEnabled,
            boolean allowPrivateNetwork,
            List<String> allowedHosts,
            List<String> sensitiveWords
    ) {
        public SecuritySettingsResponse {
            allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
            sensitiveWords = sensitiveWords == null ? List.of() : List.copyOf(sensitiveWords);
        }
    }
}
