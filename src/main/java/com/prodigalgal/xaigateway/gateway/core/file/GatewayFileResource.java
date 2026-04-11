package com.prodigalgal.xaigateway.gateway.core.file;

import org.springframework.core.io.Resource;

public record GatewayFileResource(
        String fileKey,
        String mimeType,
        String filename,
        Resource resource
) {
}
