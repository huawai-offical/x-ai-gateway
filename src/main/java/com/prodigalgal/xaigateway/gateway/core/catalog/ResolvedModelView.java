package com.prodigalgal.xaigateway.gateway.core.catalog;

import java.util.List;

public record ResolvedModelView(
        String requestedModel,
        String publicModel,
        String resolvedModelKey,
        boolean alias,
        List<CatalogCandidateView> candidates
) {
}
