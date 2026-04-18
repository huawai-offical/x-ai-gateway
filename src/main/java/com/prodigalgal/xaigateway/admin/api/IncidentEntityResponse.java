package com.prodigalgal.xaigateway.admin.api;

public record IncidentEntityResponse(
        String entityType,
        String entityRef,
        String title,
        String summary,
        String severity,
        String status,
        String source
) {
}
