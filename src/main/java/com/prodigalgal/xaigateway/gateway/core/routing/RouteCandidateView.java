package com.prodigalgal.xaigateway.gateway.core.routing;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;

public record RouteCandidateView(
        CatalogCandidateView candidate,
        Long bindingId,
        int bindingPriority,
        int bindingWeight
) {
}
