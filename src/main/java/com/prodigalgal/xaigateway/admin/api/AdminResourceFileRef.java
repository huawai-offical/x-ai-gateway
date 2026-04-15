package com.prodigalgal.xaigateway.admin.api;

public record AdminResourceFileRef(
        String fieldName,
        String fileKey,
        String filename,
        String mimeType
) {
}
