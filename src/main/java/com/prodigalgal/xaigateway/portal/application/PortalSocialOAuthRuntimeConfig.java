package com.prodigalgal.xaigateway.portal.application;

import java.util.List;
import java.util.Map;

public record PortalSocialOAuthRuntimeConfig(
        boolean enabled,
        Map<SocialOAuthProvider, ProviderConfig> providers
) {

    public ProviderConfig provider(SocialOAuthProvider provider) {
        return providers.get(provider);
    }

    public boolean enabledForLogin(SocialOAuthProvider provider) {
        ProviderConfig config = provider(provider);
        return enabled && config != null && config.configuredForLogin();
    }

    public record ProviderConfig(
            SocialOAuthProvider provider,
            boolean enabled,
            String clientId,
            String clientSecret,
            String tokenEndpoint,
            String userInfoEndpoint,
            String jwksUri,
            List<String> scopes
    ) {
        public ProviderConfig {
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }

        public boolean clientSecretConfigured() {
            return clientSecret != null && !clientSecret.isBlank();
        }

        public boolean configuredForLogin() {
            if (!enabled || clientId == null || clientId.isBlank()) {
                return false;
            }
            return provider == SocialOAuthProvider.X || clientSecretConfigured();
        }
    }
}
