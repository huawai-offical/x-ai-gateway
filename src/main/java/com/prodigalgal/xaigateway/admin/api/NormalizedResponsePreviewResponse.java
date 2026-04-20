package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record NormalizedResponsePreviewResponse(
        String surface,
        String objectMode,
        String supportStatus,
        String degradationLevel,
        List<String> notes
) {
}
