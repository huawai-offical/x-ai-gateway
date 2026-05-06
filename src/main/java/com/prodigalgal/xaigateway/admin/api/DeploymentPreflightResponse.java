package com.prodigalgal.xaigateway.admin.api;

import java.time.Instant;
import java.util.List;

public record DeploymentPreflightResponse(
        String targetVersion,
        String profile,
        String status,
        int blockingCount,
        int warningCount,
        List<DeploymentPreflightCheckResponse> checks,
        List<String> installCommands,
        List<String> upgradeCommands,
        List<String> rollbackCommands,
        List<String> documents,
        Instant generatedAt
) {
}
