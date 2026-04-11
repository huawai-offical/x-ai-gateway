package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record TlsFingerprintProfileResponse(
        Long id,
        String profileName,
        String profileCode,
        String settingsJson,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
