package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record RequestTraceDetailResponse(
        Long id,
        String requestId,
        String stage,
        String direction,
        String contentKind,
        String payloadJson,
        String metadataJson,
        String payloadHash,
        String metadataHash,
        int originalLength,
        int storedLength,
        int metadataOriginalLength,
        int metadataStoredLength,
        boolean truncated,
        boolean metadataTruncated,
        boolean redacted,
        boolean metadataRedacted,
        Instant expiresAt,
        Instant createdAt
) {
}
