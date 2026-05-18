package com.prodigalgal.xaigateway.admin.api;

public record OpenAiDirectSmokeRequest(
        Boolean dryRun,
        String baseUrl,
        Integer timeoutSeconds,
        String organization,
        String project
) {
}
