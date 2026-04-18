package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.shared.ProviderFamily;
import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.time.Instant;

public record SiteHealthScoreResponse(
        Long siteProfileId,
        String profileCode,
        String displayName,
        ProviderFamily providerFamily,
        UpstreamSiteKind siteKind,
        boolean active,
        int score,
        String healthState,
        String reason,
        int activeCredentialCount,
        int blockedCredentialCount,
        Instant effectiveUntil
) {
}
