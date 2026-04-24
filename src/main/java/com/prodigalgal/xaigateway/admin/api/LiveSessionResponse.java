package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record LiveSessionResponse(
        Long id,
        String sessionKey,
        Long distributedKeyId,
        String model,
        String protocol,
        String status,
        String resumeToken,
        long lastEventId,
        long inputAudioBytes,
        long outputAudioBytes,
        long eventCount,
        String metadataJson,
        Instant expiresAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
