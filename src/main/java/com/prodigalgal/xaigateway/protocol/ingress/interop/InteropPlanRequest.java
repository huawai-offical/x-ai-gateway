package com.prodigalgal.xaigateway.protocol.ingress.interop;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record InteropPlanRequest(
        @NotBlank(message = "协议不能为空。")
        String protocol,
        String requestPath,
        String requestedModel,
        String degradationPolicy,
        String clientFamily,
        JsonNode body
) {
    public InteropPlanRequest(
            String protocol,
            String requestPath,
            String requestedModel,
            String degradationPolicy,
            JsonNode body
    ) {
        this(protocol, requestPath, requestedModel, degradationPolicy, null, body);
    }
}
