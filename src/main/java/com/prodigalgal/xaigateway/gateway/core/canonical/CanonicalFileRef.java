package com.prodigalgal.xaigateway.gateway.core.canonical;

public record CanonicalFileRef(
        String fieldName,
        String fileKey,
        String filename,
        String mimeType
) {
}
