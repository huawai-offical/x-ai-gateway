package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;

public record ModelAliasPreviewRequest(
        @NotBlank(message = "待测试模型不能为空。")
        String requestedModel,
        @NotBlank(message = "协议不能为空。")
        String protocol
) {
}
