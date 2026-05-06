package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalSocialOAuthIdentityResponse(
        Long id,
        String provider,
        String externalSubject,
        String email,
        String displayName,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
}
