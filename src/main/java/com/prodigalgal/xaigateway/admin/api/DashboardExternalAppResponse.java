package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record DashboardExternalAppResponse(
        Long id,
        String appName,
        String slug,
        String iframeUrl,
        String allowedOrigin,
        String sandboxPermissions,
        String signingSecretFingerprint,
        boolean enabled,
        boolean navEnabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
