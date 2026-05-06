package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ClientInstanceAuthorizationResponse(
        Long clientInstanceId,
        String instanceId,
        String clientFamily,
        String grantToken,
        Instant expiresAt,
        boolean consumed,
        boolean revoked,
        String deepLinkUrl,
        String pluginMessageJson,
        String warning
) {
}
