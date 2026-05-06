package com.prodigalgal.xaigateway.admin.api;

public record ProviderSitePresetImportRequest(
        Boolean active,
        Boolean refreshCapabilities
) {
}
