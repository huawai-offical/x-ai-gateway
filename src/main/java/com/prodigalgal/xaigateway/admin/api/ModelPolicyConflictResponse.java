package com.prodigalgal.xaigateway.admin.api;

public record ModelPolicyConflictResponse(
        String severity,
        String code,
        String message,
        Long policyId
) {
}
