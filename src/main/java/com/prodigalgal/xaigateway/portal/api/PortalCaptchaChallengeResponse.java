package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;

public record PortalCaptchaChallengeResponse(
        String challengeId,
        String question,
        Instant expiresAt
) {
}
