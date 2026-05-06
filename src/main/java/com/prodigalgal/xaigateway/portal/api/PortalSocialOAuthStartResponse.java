package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalSocialOAuthStartResponse(
        String provider,
        String state,
        String authorizationUrl,
        Instant expiresAt
) {
}
