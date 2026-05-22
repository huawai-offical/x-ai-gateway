package com.prodigalgal.xaigateway.admin.api;

import java.util.List;

public record ModelPolicyPreviewResponse(
        String requestedModel,
        String publicModel,
        String resolvedModelKey,
        String selectedUpstreamModelKey,
        String protocol,
        List<String> governanceNotes,
        List<ModelPolicyCandidatePreviewResponse> candidates
) {
}
