package com.prodigalgal.xaigateway.admin.api;

import jakarta.validation.constraints.NotBlank;

public record WebhookEndpointRequest(
        @NotBlank String endpointName,
        @NotBlank String endpointUrl,
        String secret,
        String signingMode,
        Integer timeoutMs,
        Boolean enabled
) {
}
