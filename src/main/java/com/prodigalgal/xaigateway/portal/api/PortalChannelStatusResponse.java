package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;
import java.util.List;

public record PortalChannelStatusResponse(
        Long siteProfileId,
        String profileCode,
        String displayName,
        String siteKind,
        boolean active,
        String healthState,
        String blockedReason,
        List<String> supportedProtocols,
        Instant refreshedAt
) {
}
