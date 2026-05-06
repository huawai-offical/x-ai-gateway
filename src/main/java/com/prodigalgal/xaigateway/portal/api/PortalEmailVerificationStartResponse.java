package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalEmailVerificationStartResponse(
        String verificationId,
        String verificationCode,
        Instant expiresAt
) {
}
