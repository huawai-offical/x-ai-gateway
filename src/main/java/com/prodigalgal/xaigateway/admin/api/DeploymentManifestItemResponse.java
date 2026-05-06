package com.prodigalgal.xaigateway.admin.api;

public record DeploymentManifestItemResponse(
        String path,
        String purpose,
        boolean required,
        boolean present
) {
}
