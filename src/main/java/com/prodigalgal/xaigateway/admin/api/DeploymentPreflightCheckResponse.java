package com.prodigalgal.xaigateway.admin.api;

public record DeploymentPreflightCheckResponse(
        String code,
        String status,
        boolean blocking,
        String message,
        String remediation
) {
}
