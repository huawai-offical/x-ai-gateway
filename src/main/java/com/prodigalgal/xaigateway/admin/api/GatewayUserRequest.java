package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;

public record GatewayUserRequest(
        @NotBlank(message = "用户邮箱不能为空。")
        String email,
        String displayName,
        Boolean active,
        String notes
) {
}
