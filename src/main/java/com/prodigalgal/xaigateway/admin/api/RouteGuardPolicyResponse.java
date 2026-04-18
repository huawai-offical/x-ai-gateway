package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyMode;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceTargetType;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.time.Instant;

public record RouteGuardPolicyResponse(
        Long id,
        String policyName,
        GovernanceTargetType targetType,
        ProviderType providerType,
        Long siteProfileId,
        Long credentialId,
        Long accountId,
        Long proxyId,
        GovernancePolicyMode policyMode,
        GovernanceActionType actionType,
        Integer ttlSeconds,
        Instant effectiveUntil,
        int priority,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
