package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;

public record ReleaseArtifactResponse(
        Long id,
        String versionName,
        String artifactRef,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
