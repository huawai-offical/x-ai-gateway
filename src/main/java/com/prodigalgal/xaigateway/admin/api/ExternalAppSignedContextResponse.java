package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ExternalAppSignedContextResponse(
        String slug,
        String origin,
        String context,
        String signature,
        String launchUrl,
        Instant expiresAt
) {
}
