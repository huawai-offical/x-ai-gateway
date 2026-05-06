package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ClientInstanceResponse(
        Long id,
        Long distributedKeyId,
        String distributedKeyName,
        String maskedKey,
        String instanceId,
        String displayName,
        String clientFamily,
        String workspaceHint,
        String pluginName,
        String pluginVersion,
        String deepLinkScheme,
        String status,
        Instant lastAuthorizedAt,
        Instant lastSeenAt,
        Instant lastRequestAt,
        String lastRequestId,
        Instant disabledAt,
        Instant revokedAt,
        String metadataJson,
        Instant createdAt,
        Instant updatedAt
) {
}
