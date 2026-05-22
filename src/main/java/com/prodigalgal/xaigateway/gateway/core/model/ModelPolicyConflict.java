package com.prodigalgal.xaigateway.gateway.core.model;

public record ModelPolicyConflict(
        String severity,
        String code,
        String message,
        Long policyId
) {
}
