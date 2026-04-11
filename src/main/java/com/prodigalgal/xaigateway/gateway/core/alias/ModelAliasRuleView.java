package com.prodigalgal.xaigateway.gateway.core.alias;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;

public record ModelAliasRuleView(
        Long id,
        String protocol,
        String targetModelName,
        String targetModelKey,
        ProviderType providerType,
        String baseUrlPattern,
        int priority
) {
}
