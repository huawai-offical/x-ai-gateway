package com.prodigalgal.xaigateway.portal.api;

public record PortalKeyCreateResponse(
        PortalKeyResponse key,
        String fullKey,
        String secretNotice
) {
}
