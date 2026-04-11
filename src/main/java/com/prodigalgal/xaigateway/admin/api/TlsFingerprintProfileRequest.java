package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;

public record TlsFingerprintProfileRequest(
        @NotBlank String profileName,
        @NotBlank String profileCode,
        String settingsJson,
        String description,
        Boolean active
) {
}
