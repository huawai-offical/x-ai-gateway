package com.prodigalgal.xaigateway.admin.api;

public record ProviderCatalogMarketplaceUpdateRequest(
        String remoteUrl,
        String catalogJson,
        String signature,
        String signingKey,
        Boolean dryRun
) {
}
