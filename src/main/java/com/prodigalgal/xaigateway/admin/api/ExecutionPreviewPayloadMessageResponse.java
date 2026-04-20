package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record ExecutionPreviewPayloadMessageResponse(
        String role,
        String text,
        List<ExecutionPreviewPayloadPartResponse> parts
) {
}
