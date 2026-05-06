package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalSecurityStatusResponse(
        boolean emailVerified,
        Instant emailVerifiedAt,
        boolean totpEnabled,
        Instant totpVerifiedAt,
        boolean passkeyEnabled,
        int passkeyCount,
        boolean emailVerificationRequiredForKeyCreation
) {
}
