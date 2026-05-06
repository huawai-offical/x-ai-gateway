package com.prodigalgal.xaigateway.portal.api;

import jakarta.validation.constraints.NotBlank;

public record PortalEmailVerificationConfirmRequest(
        @NotBlank String verificationId,
        @NotBlank String verificationCode
) {
}
