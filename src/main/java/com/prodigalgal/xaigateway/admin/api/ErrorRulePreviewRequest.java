package com.prodigalgal.xaigateway.admin.api;

public record ErrorRulePreviewRequest(
        String providerType,
        String protocol,
        String model,
        String requestPath,
        Integer httpStatus,
        String errorCode,
        String matchScope,
        String message
) {
}
