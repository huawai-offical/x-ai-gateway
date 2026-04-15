package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

public record AdminChatExecuteRequest(
        @NotBlank(message = "distributedKeyPrefix 不能为空。")
        String distributedKeyPrefix,
        @NotBlank(message = "协议不能为空。")
        String protocol,
        @NotBlank(message = "请求路径不能为空。")
        String requestPath,
        @NotBlank(message = "模型不能为空。")
        String requestedModel,
        JsonNode body
) {
}
