package com.prodigalgal.xaigateway.gateway.core.file;

public record GatewayFileContent(
        GatewayFileResponse metadata,
        byte[] bytes,
        String mimeType
) {
}
