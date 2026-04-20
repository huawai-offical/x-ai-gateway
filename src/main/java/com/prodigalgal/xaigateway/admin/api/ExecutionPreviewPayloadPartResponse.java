package com.prodigalgal.xaigateway.admin.api;

public record ExecutionPreviewPayloadPartResponse(
        String type,
        String text,
        String mimeType,
        String uri,
        String name,
        String toolCallId,
        String toolName
) {
}
