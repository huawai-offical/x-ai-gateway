package com.prodigalgal.xaigateway.gateway.core.governance;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;

public record GovernanceContext(
        ProviderType providerType,
        Long siteProfileId,
        Long credentialId,
        Long accountId,
        Long proxyId
) {
}
