package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record NativeTranslationConformanceRow(
        String provider,
        String protocol,
        String endpoint,
        String supportLevel,
        List<String> supportedFeatures,
        List<String> lossyFeatures,
        List<String> unsupportedFeatures,
        String verification,
        String notes
) {
}
