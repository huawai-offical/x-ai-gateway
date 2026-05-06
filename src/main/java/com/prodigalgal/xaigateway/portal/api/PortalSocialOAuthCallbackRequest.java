package com.prodigalgal.xaigateway.portal.api;

import jakarta.validation.constraints.NotBlank;

public record PortalSocialOAuthCallbackRequest(
        @NotBlank String state,
        @NotBlank String code,
        String externalSubject,
        String email,
        String displayName,
        String metadataJson
) {
}
