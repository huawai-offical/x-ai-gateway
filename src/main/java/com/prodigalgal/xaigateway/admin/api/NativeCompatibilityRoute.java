package com.prodigalgal.xaigateway.admin.api;

public record NativeCompatibilityRoute(
        String protocol,
        String namespace,
        String method,
        String path,
        String status,
        boolean authenticated,
        String governanceMode,
        String notes
) {
}
