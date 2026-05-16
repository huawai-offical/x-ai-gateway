package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record NativeCompatibilityResponse(
        List<NativeCompatibilityRoute> routes,
        List<NativeTranslationConformanceRow> translationConformance
) {
}
