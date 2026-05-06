package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalPasskeyRegistrationStartResponse(
        String challengeId,
        String challenge,
        String rpId,
        String origin,
        String userHandle,
        String userName,
        String credentialName,
        Instant expiresAt
) {
}
