package com.prodigalgal.xaigateway.admin.api;

import java.util.List;
import java.util.Map;

public record ExecutionPreviewPayloadResponse(
        String providerType,
        String resolvedModel,
        String requestPath,
        String objectMode,
        List<ExecutionPreviewPayloadMessageResponse> messages,
        Map<String, Object> providerOptions
) {
}
