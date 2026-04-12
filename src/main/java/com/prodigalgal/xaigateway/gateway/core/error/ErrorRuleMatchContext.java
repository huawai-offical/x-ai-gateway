package com.prodigalgal.xaigateway.gateway.core.error;

public record ErrorRuleMatchContext(
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
