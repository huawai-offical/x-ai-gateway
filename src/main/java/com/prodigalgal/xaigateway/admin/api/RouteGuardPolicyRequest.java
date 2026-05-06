package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceActionType;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernancePolicyMode;
import com.prodigalgal.xaigateway.gateway.core.governance.GovernanceTargetType;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RouteGuardPolicyRequest(
        @NotBlank String policyName,
        @NotNull GovernanceTargetType targetType,
        ProviderType providerType,
        Long siteProfileId,
        Long credentialId,
        Long accountId,
        Long proxyId,
        @NotNull GovernancePolicyMode policyMode,
        @NotNull GovernanceActionType actionType,
        Integer ttlSeconds,
        Integer priority,
        Boolean enabled,
        String description,
        String retryPolicy,
        String fallbackPolicy,
        String circuitBreakerPolicy,
        String rateLimitPolicy
) {
}
