package com.prodigalgal.xaigateway.admin.api;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

public record RouteSelectionPreviewRequest(
        @NotBlank(message = "distributedKeyPrefix 不能为空。")
        String distributedKeyPrefix,
        @NotBlank(message = "协议不能为空。")
        String protocol,
        @NotBlank(message = "请求路径不能为空。")
        String requestPath,
        String httpMethod,
        @NotBlank(message = "模型不能为空。")
        String requestedModel,
        String clientFamily,
        JsonNode requestBody
) {
    public RouteSelectionPreviewRequest(
            String distributedKeyPrefix,
            String protocol,
            String requestPath,
            String requestedModel,
            String clientFamily,
            JsonNode requestBody) {
        this(distributedKeyPrefix, protocol, requestPath, null, requestedModel, clientFamily, requestBody);
    }
}
