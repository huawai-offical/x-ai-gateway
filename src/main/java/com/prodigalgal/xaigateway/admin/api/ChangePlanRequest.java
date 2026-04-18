package com.prodigalgal.xaigateway.admin.api;

public record ChangePlanRequest(
        String planName,
        String planType,
        String executionClass,
        Long releaseArtifactId,
        Long recoveryCheckpointId,
        Long maintenanceWindowId,
        String requestedBy,
        Boolean manualOverride,
        String overrideReason,
        String emergencyReason
) {
}
