package com.prodigalgal.xaigateway.admin.api;

public record ReleaseArtifactRequest(
        String versionName,
        String artifactRef,
        Boolean active
) {
}
