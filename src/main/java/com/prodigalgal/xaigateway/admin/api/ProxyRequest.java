package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;

public record ProxyRequest(
        @NotBlank String proxyName,
        @NotBlank String proxyUrl,
        String description,
        Boolean active
) {
}
