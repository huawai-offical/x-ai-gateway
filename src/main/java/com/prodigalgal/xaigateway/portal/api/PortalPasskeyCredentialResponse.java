package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;
import java.util.List;

public record PortalPasskeyCredentialResponse(
        Long id,
        String credentialId,
        String credentialName,
        String rpId,
        String origin,
        List<String> transports,
        long signCount,
        Instant lastUsedAt,
        Instant createdAt
) {
}
