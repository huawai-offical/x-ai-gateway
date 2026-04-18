package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record RollbackPlaybookResponse(
        Long id,
        Long recoveryCheckpointId,
        Long rollbackReleaseArtifactId,
        String status,
        String triggerConditionsJson,
        Long latestRollbackPlanId,
        Instant lastTriggeredAt,
        Instant createdAt,
        Instant updatedAt
) {
}
