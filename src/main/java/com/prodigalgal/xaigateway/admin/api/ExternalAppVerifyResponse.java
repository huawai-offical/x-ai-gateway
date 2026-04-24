package com.prodigalgal.xaigateway.admin.api;

public record ExternalAppVerifyResponse(
        boolean valid,
        String reason,
        String slug
) {
}
