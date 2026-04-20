package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record AdminAuthSettingsResponse(
        String username,
        boolean persisted,
        String credentialSource,
        Instant initializedAt,
        Instant updatedAt
) {
}
