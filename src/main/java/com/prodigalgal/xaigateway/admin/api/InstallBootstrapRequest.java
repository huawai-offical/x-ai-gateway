package com.prodigalgal.xaigateway.admin.api;

public record InstallBootstrapRequest(
        String adminEmail,
        String environmentName
) {
}
