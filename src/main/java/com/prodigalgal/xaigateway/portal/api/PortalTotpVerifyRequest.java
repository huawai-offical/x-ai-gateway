package com.prodigalgal.xaigateway.portal.api;

import jakarta.validation.constraints.NotBlank;

public record PortalTotpVerifyRequest(
        @NotBlank String code
) {
}
