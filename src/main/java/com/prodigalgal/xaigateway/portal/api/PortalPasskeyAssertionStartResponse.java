package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;
import java.util.List;

public record PortalPasskeyAssertionStartResponse(
        String challengeId,
        String challenge,
        String rpId,
        String origin,
        List<String> allowedCredentialIds,
        Instant expiresAt
) {
}
