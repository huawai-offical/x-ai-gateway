package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record CanonicalResourceRequest(
        String distributedKeyPrefix,
        CanonicalIngressProtocol ingressProtocol,
        String requestPath,
        String requestedModel,
        TranslationResourceType resourceType,
        TranslationOperation operation,
        JsonNode jsonBody,
        Map<String, String> multipartMeta,
        List<String> gatewayFileRefs,
        boolean stream
) {
    public CanonicalResourceRequest {
        multipartMeta = multipartMeta == null ? Map.of() : Map.copyOf(multipartMeta);
        gatewayFileRefs = gatewayFileRefs == null ? List.of() : List.copyOf(gatewayFileRefs);
    }
}
