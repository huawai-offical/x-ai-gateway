package com.prodigalgal.xaigateway.portal.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PortalRegisterRequest(
        @NotBlank(message = "邮箱不能为空。")
        String email,
        String displayName,
        @NotBlank(message = "密码不能为空。")
        @Size(min = 8, max = 128, message = "密码长度需要在 8 到 128 个字符之间。")
        String password
) {
}
