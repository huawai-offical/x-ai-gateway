package com.prodigalgal.xaigateway.admin.api;

public record ChangePlanExecuteRequest(
        String actor,
        Boolean manualOverride,
        String overrideReason,
        String emergencyReason,
        Boolean simulateFailure
) {
}
