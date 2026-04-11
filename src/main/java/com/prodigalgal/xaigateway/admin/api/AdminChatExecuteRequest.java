package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;

public record AdminChatExecuteRequest(
        @NotBlank(message = "distributedKeyPrefix 不能为空。")
        String distributedKeyPrefix,
        @NotBlank(message = "协议不能为空。")
        String protocol,
        @NotBlank(message = "请求路径不能为空。")
        String requestPath,
        @NotBlank(message = "模型不能为空。")
        String requestedModel,
        String systemPrompt,
        @NotBlank(message = "用户提示词不能为空。")
        String userPrompt,
        Double temperature,
        Integer maxTokens
) {
}
