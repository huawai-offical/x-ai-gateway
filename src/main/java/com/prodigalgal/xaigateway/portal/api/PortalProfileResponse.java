package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalProfileResponse(
        Long userId,
        String email,
        String displayName,
        boolean active,
        boolean emailVerified,
        boolean totpEnabled,
        int passkeyCount,
        Instant lastLoginAt,
        Instant createdAt
) {
}
