package com.prodigalgal.xaigateway.admin.application;

import java.time.Instant;

public record LiveSessionRuntimeRequest(
        String sessionKey,
        String model,
        String protocol,
        String resumeToken,
        String metadataJson,
        Instant now
) {
}
