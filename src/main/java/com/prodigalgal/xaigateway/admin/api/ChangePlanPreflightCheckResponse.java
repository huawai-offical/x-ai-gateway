package com.prodigalgal.xaigateway.admin.api;

public record ChangePlanPreflightCheckResponse(
        String checkName,
        String status,
        boolean blocking,
        String message
) {
}
