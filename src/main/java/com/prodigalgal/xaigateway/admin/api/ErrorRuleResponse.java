package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ErrorRuleResponse(
        Long id,
        boolean enabled,
        int priority,
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
        String downgradePolicy,
        Instant createdAt,
        Instant updatedAt
) {
}
