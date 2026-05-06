package com.prodigalgal.xaigateway.admin.application;

import com.prodigalgal.xaigateway.gateway.core.shared.UpstreamSiteKind;
import java.util.List;
import java.util.Locale;

public record ProviderPresetDefinition(
        String code,
        String displayName,
        UpstreamSiteKind siteKind,
        String defaultBaseUrl,
        String description,
        List<String> capabilityTags,
        String costProfile,
        String errorMode,
        String catalogVersion,
        String catalogSource,
        boolean deprecated,
        List<String> conformanceChecks,
        String compatibilitySurface,
        String supportStrategy,
        List<String> modelFamilies,
        String pricingMetadata,
        List<String> unsupportedFeatures
) {
    public ProviderPresetDefinition {
        code = code == null ? null : code.trim().toLowerCase(Locale.ROOT);
        capabilityTags = capabilityTags == null ? List.of() : List.copyOf(capabilityTags);
        conformanceChecks = conformanceChecks == null ? List.of() : List.copyOf(conformanceChecks);
        compatibilitySurface = compatibilitySurface == null || compatibilitySurface.isBlank()
                ? "openai-compatible-chat"
                : compatibilitySurface;
        supportStrategy = supportStrategy == null || supportStrategy.isBlank()
                ? "cloud-openai-compatible"
                : supportStrategy;
        modelFamilies = modelFamilies == null ? List.of() : List.copyOf(modelFamilies);
        pricingMetadata = pricingMetadata == null ? "" : pricingMetadata;
        unsupportedFeatures = unsupportedFeatures == null ? List.of() : List.copyOf(unsupportedFeatures);
    }

    public String profileCode() {
        return "preset:" + code;
    }
}
