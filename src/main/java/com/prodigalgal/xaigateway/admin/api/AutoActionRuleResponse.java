package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceRecoveryMode;
import java.time.Instant;

public record AutoActionRuleResponse(
        Long id,
        String ruleName,
        String eventType,
        String severity,
        String entityType,
        GovernanceActionType actionType,
        Integer ttlSeconds,
        GovernanceRecoveryMode recoveryMode,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
