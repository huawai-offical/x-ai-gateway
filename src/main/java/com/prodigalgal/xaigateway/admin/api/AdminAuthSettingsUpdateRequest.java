package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;

public record AdminAuthSettingsUpdateRequest(
        @NotBlank(message = "username 不能为空。")
        String username,
        @NotBlank(message = "currentPassword 不能为空。")
        String currentPassword,
        String newPassword
) {
}
