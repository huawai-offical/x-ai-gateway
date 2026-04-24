package com.prodigalgal.xaigateway.admin.api;

public record MaintenanceRunRequest(
        String runType,
        Boolean dryRun,
        Boolean confirm,
        String actor,
        String sourceRef,
        String detailJson
) {
}
