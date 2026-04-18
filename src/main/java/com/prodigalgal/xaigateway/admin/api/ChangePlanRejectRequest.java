package com.prodigalgal.xaigateway.admin.api;

public record ChangePlanRejectRequest(
        String rejectedBy,
        String reason
) {
}
