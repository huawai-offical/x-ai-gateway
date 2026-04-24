package com.prodigalgal.xaigateway.admin.api;

public record ExternalAppVerifyRequest(
        String origin,
        String context,
        String signature
) {
}
