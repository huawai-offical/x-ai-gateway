package com.prodigalgal.xaigateway.portal.application;

import java.util.List;

public record PortalSocialOAuthSettingsUpdate(
        Boolean enabled,
        List<ProviderUpdate> providers
) {
    public record ProviderUpdate(
            String provider,
            Boolean enabled,
            String clientId,
            String clientSecret,
            Boolean clearClientSecret
    ) {
    }
}
