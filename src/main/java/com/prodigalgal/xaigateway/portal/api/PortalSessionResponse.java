package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalSessionResponse(
        boolean authenticated,
        Long userId,
        String email,
        String displayName,
        Instant authenticatedAt,
        Instant expiresAt
) {

    public static PortalSessionResponse unauthenticated() {
        return new PortalSessionResponse(false, null, null, null, null, null);
    }
}
