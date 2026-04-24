package com.prodigalgal.xaigateway.admin.api;

public record DashboardExternalAppRequest(
        String appName,
        String slug,
        String iframeUrl,
        String allowedOrigin,
        String sandboxPermissions,
        String signingSecret,
        Boolean enabled,
        Boolean navEnabled,
        String description
) {
}
