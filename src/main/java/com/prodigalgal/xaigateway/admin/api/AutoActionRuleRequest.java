package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceRecoveryMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AutoActionRuleRequest(
        @NotBlank String ruleName,
        @NotBlank String eventType,
        String severity,
        String entityType,
        @NotNull GovernanceActionType actionType,
        Integer ttlSeconds,
        @NotNull GovernanceRecoveryMode recoveryMode,
        Boolean enabled,
        String description
) {
}
