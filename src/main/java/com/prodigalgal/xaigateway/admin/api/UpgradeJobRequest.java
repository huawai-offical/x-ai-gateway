package com.prodigalgal.xaigateway.admin.api;

public record UpgradeJobRequest(
        Long targetReleaseArtifactId,
        Boolean forceFailure,
        Boolean confirm
) {
}
