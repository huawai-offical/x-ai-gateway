package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record DeploymentManifestResponse(
        String profile,
        String imageName,
        String composeFile,
        String envExample,
        String installScript,
        String upgradeScript,
        String rollbackScript,
        String healthEndpoint,
        List<DeploymentManifestItemResponse> requiredFiles,
        List<String> environmentVariables,
        List<String> volumes,
        List<String> commands,
        List<String> documents,
        Instant generatedAt
) {
}
