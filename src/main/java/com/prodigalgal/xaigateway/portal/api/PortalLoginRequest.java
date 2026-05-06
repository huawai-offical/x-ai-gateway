package com.prodigalgal.xaigateway.portal.api;

import jakarta.validation.constraints.NotBlank;

public record PortalLoginRequest(
        @NotBlank(message = "邮箱不能为空。")
        String email,
        @NotBlank(message = "密码不能为空。")
        String password,
        String totpCode
) {
    public PortalLoginRequest(String email, String password) {
        this(email, password, null);
    }
}
