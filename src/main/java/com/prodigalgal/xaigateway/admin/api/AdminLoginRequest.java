package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminLoginRequest(
        @NotBlank(message = "username 不能为空。")
        String username,
        @NotBlank(message = "password 不能为空。")
        String password,
        @NotBlank(message = "challengeId 不能为空。")
        String challengeId,
        @NotNull(message = "mathAnswer 不能为空。")
        Integer mathAnswer,
        @NotBlank(message = "powNonce 不能为空。")
        String powNonce
) {
}
