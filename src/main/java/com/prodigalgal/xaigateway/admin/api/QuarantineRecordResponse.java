package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceRecoveryMode;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceTargetType;
import com.prodigalgal.xaigateway.gateway.core.governance.QuarantineStatus;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record QuarantineRecordResponse(
        Long id,
        GovernanceTargetType targetType,
        ProviderType providerType,
        Long siteProfileId,
        Long credentialId,
        Long accountId,
        Long proxyId,
        Long sourceRuleId,
        Long sourceEventId,
        GovernanceActionType actionType,
        GovernanceRecoveryMode recoveryMode,
        String reason,
        QuarantineStatus status,
        Instant startedAt,
        Instant expiresAt,
        Instant releasedAt,
        String releaseReason,
        Instant createdAt,
        Instant updatedAt
) {
}
