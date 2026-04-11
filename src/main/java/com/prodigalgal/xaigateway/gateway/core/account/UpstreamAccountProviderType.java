package com.prodigalgal.xaigateway.gateway.core.account;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;

public enum UpstreamAccountProviderType {
    OPENAI_OAUTH(ProviderType.OPENAI_DIRECT),
    GEMINI_OAUTH(ProviderType.GEMINI_DIRECT),
    CLAUDE_ACCOUNT(ProviderType.ANTHROPIC_DIRECT);

    private final ProviderType routeProviderType;

    UpstreamAccountProviderType(ProviderType routeProviderType) {
        this.routeProviderType = routeProviderType;
    }

    public ProviderType routeProviderType() {
        return routeProviderType;
    }
}
