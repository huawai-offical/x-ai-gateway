package com.prodigalgal.xaigateway.portal.api;

public record PortalTotpSetupResponse(
        String secret,
        String otpauthUri
) {
}
