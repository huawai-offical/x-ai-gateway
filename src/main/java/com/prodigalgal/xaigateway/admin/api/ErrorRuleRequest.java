package com.prodigalgal.xaigateway.admin.api;

public record ErrorRuleRequest(
        Boolean enabled,
        Integer priority,
        String providerType,
        String protocol,
        String modelPattern,
        String requestPath,
        Integer httpStatus,
        String errorCode,
        String matchScope,
        String action,
        Integer rewriteStatus,
        String rewriteCode,
        String rewriteMessage,
        String downgradePolicy
) {
}
