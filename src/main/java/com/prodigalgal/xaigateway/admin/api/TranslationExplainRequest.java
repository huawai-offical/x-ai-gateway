package com.prodigalgal.xaigateway.admin.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record TranslationExplainRequest(
        @NotBlank(message = "distributedKeyPrefix 不能为空。")
        String distributedKeyPrefix,
        @NotBlank(message = "protocol 不能为空。")
        String protocol,
        @NotBlank(message = "requestPath 不能为空。")
        String requestPath,
        @NotBlank(message = "requestedModel 不能为空。")
        String requestedModel,
        String degradationPolicy,
        JsonNode body
) {
}
