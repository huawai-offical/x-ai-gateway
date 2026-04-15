package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record CanonicalResourceResponse(
        TranslationResourceType resourceType,
        TranslationOperation operation,
        String responseKind,
        String objectType,
        String objectId,
        String status,
        List<CanonicalResourceEvent> events,
        List<CanonicalResourceDegradation> degradations,
        JsonNode body,
        Integer binaryLength,
        Map<String, Object> metadata
) {
    public CanonicalResourceResponse {
        resourceType = resourceType == null ? TranslationResourceType.UNKNOWN : resourceType;
        operation = operation == null ? TranslationOperation.UNKNOWN : operation;
        responseKind = responseKind == null || responseKind.isBlank() ? "object" : responseKind;
        events = events == null ? List.of() : List.copyOf(events);
        degradations = degradations == null ? List.of() : List.copyOf(degradations);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
