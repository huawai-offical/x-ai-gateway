package com.prodigalgal.xaigateway.gateway.core.auth;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;

public record DistributedCredentialBindingView(
        Long bindingId,
        Long credentialId,
        String credentialName,
        ProviderType providerType,
        String baseUrl,
        int priority,
        int weight
) {
}
