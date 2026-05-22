package com.prodigalgal.xaigateway.gateway.core.model;

import com.prodigalgal.xaigateway.gateway.core.catalog.CatalogCandidateView;
import java.util.List;

public record ModelPolicyResolvedModel(
        String requestedModel,
        String publicModel,
        String resolvedModelKey,
        String modelGroup,
        boolean policyMapped,
        List<CatalogCandidateView> candidates,
        List<String> notes
) {
}
