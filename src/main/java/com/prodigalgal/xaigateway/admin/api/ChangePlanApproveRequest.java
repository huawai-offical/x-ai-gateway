package com.prodigalgal.xaigateway.admin.api;

public record ChangePlanApproveRequest(
        String approvedBy,
        String reason
) {
}
