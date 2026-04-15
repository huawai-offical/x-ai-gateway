package com.prodigalgal.xaigateway.gateway.core.canonical;

import tools.jackson.databind.JsonNode;

public interface CanonicalResourceMapper {

    default CanonicalResourceResponse mapJson(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            JsonNode rawBody) {
        return new CanonicalResourceResponse(
                request == null ? null : request.resourceType(),
                request == null ? null : request.operation(),
                "object",
                null,
                null,
                null,
                java.util.List.of(),
                java.util.List.of(),
                rawBody,
                null,
                java.util.Map.of()
        );
    }

    default CanonicalResourceResponse mapBinary(
            CanonicalResourceRequest request,
            CanonicalExecutionPlan plan,
            byte[] rawBody,
            String contentType) {
        return new CanonicalResourceResponse(
                request == null ? null : request.resourceType(),
                request == null ? null : request.operation(),
                "binary",
                null,
                null,
                null,
                java.util.List.of(),
                java.util.List.of(),
                null,
                rawBody == null ? 0 : rawBody.length,
                contentType == null ? java.util.Map.of() : java.util.Map.of("contentType", contentType)
        );
    }
}
