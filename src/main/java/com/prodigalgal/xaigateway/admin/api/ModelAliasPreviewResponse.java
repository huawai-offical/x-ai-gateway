package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import java.util.List;

public record ModelAliasPreviewResponse(
        String requestedModel,
        String protocol,
        boolean aliasMatched,
        String publicModel,
        String resolvedModelKey,
        int candidateCount,
        List<CatalogCandidateView> candidates
) {
}
