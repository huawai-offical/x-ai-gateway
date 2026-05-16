package com.prodigalgal.xaigateway.admin.api;

public record OfficialCodexResponsesSmokeRequest(
        String model,
        String input,
        Boolean dryRun,
        String baseUrl,
        Integer timeoutSeconds
) {
    public OfficialCodexResponsesSmokeRequest(String model, String input, Boolean dryRun) {
        this(model, input, dryRun, null, null);
    }
}
