package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record ChangePlanResponse(
        Long id,
        String planName,
        String planType,
        String executionClass,
        String status,
        Long releaseArtifactId,
        Long recoveryCheckpointId,
        Long maintenanceWindowId,
        Long rollbackPlaybookId,
        String requestedBy,
        String approvedBy,
        boolean manualOverride,
        String overrideReason,
        String emergencyReason,
        String riskLevel,
        String currentStage,
        String currentMessage,
        List<ChangePlanPreflightCheckResponse> preflightChecks,
        List<ApprovalRecordResponse> approvals,
        List<ReleaseRolloutStageResponse> rolloutStages,
        RollbackPlaybookResponse rollbackPlaybook,
        Instant createdAt,
        Instant updatedAt
) {
    public ChangePlanResponse {
        preflightChecks = preflightChecks == null ? List.of() : List.copyOf(preflightChecks);
        approvals = approvals == null ? List.of() : List.copyOf(approvals);
        rolloutStages = rolloutStages == null ? List.of() : List.copyOf(rolloutStages);
    }
}
