package com.prodigalgal.xaigateway.portal.api;

import java.util.List;

public record PortalSocialOAuthProviderResponse(
        String provider,
        String displayName,
        String authorizationEndpoint,
        List<String> defaultScopes
) {
}
