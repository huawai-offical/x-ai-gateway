package com.prodigalgal.xaigateway.portal.application;

import java.time.Instant;
import java.util.List;

public record PortalSocialOAuthSettingsView(
        boolean enabled,
        List<ProviderView> providers,
        Instant updatedAt
) {
    public PortalSocialOAuthSettingsView {
        providers = providers == null ? List.of() : List.copyOf(providers);
    }

    public record ProviderView(
            String provider,
            String displayName,
            boolean enabled,
            String clientId,
            boolean clientSecretConfigured,
            List<String> scopes,
            boolean configuredForLogin
    ) {
        public ProviderView {
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }
    }
}
