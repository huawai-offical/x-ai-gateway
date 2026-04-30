package com.prodigalgal.xaigateway.admin.api;

import java.util.List;
import tools.jackson.databind.JsonNode;

public record AdminResourceTemplateResponse(
        String resourceType,
        String operation,
        String executionSurface,
        String protocol,
        String method,
        String requestPath,
        String modelHint,
        String description,
        JsonNode bodyTemplate,
        List<String> formFields,
        List<String> fileFields,
        List<String> resultSignals
) {
}
