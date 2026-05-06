package com.prodigalgal.xaigateway.protocol.ingress.publicapi;

public record PublicDocsErrorCodeResponse(
        String code,
        int httpStatus,
        String description,
        String remediation
) {
}
