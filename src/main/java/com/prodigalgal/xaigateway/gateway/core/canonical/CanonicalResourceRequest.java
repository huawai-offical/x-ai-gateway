package com.prodigalgal.xaigateway.gateway.core.canonical;

import com.prodigalgal.xaigateway.gateway.core.interop.TranslationOperation;
import com.prodigalgal.xaigateway.gateway.core.interop.TranslationResourceType;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public record CanonicalResourceRequest(
        String distributedKeyPrefix,
        CanonicalIngressProtocol ingressProtocol,
        String httpMethod,
        String requestPath,
        String normalizedPath,
        Map<String, String> pathParams,
        String requestedModel,
        TranslationResourceType resourceType,
        TranslationOperation operation,
        JsonNode jsonBody,
        Map<String, String> formFields,
        List<CanonicalFileRef> fileRefs,
        boolean expectsBinary,
        boolean stream
) {
    public CanonicalResourceRequest {
        httpMethod = httpMethod == null || httpMethod.isBlank() ? "POST" : httpMethod.trim().toUpperCase();
        normalizedPath = normalizedPath == null || normalizedPath.isBlank() ? requestPath : normalizedPath;
        pathParams = pathParams == null ? Map.of() : Map.copyOf(pathParams);
        formFields = formFields == null ? Map.of() : Map.copyOf(formFields);
        fileRefs = fileRefs == null ? List.of() : List.copyOf(fileRefs);
    }

    public CanonicalResourceRequest(
            String distributedKeyPrefix,
            CanonicalIngressProtocol ingressProtocol,
            String requestPath,
            String requestedModel,
            TranslationResourceType resourceType,
            TranslationOperation operation,
            JsonNode jsonBody,
            Map<String, String> formFields,
            List<CanonicalFileRef> fileRefs,
            boolean stream
    ) {
        this(
                distributedKeyPrefix,
                ingressProtocol,
                "POST",
                requestPath,
                requestPath,
                Map.of(),
                requestedModel,
                resourceType,
                operation,
                jsonBody,
                formFields,
                fileRefs,
                false,
                stream
        );
    }
}
