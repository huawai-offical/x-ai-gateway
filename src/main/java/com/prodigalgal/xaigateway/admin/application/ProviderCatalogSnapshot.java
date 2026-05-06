package com.prodigalgal.xaigateway.admin.application;

import java.util.List;

public record ProviderCatalogSnapshot(
        String version,
        String source,
        List<ProviderPresetDefinition> presets
) {
    public ProviderCatalogSnapshot {
        version = version == null || version.isBlank() ? "unknown" : version;
        source = source == null || source.isBlank() ? "builtin" : source;
        presets = presets == null ? List.of() : List.copyOf(presets);
    }
}
