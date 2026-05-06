package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ClientInstanceAuthorizationRequest(
        String format,
        String baseUrl,
        String source,
        Integer ttlSeconds,
        Instant expiresAt,
        String fullKey,
        String secretExportGrantToken,
        String pluginName,
        String pluginVersion
) {
}
