package com.prodigalgal.xaigateway.admin.api;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateEvaluation;
import com.prodigalgal.xaigateway.gateway.core.shared.ProviderType;
import java.util.List;

public record ModelPolicyCandidatePreviewResponse(
        Long credentialId,
        String credentialName,
        ProviderType providerType,
        Long siteProfileId,
        String upstreamModel,
        String upstreamModelKey,
        boolean eligible,
        String healthState,
        int bindingPriority,
        int bindingWeight,
        double totalScore,
        List<String> scoreBreakdown,
        List<String> exclusionReasons
) {
    public static ModelPolicyCandidatePreviewResponse from(RouteCandidateEvaluation evaluation) {
        var routeCandidate = evaluation.candidate();
        var candidate = routeCandidate.candidate();
        return new ModelPolicyCandidatePreviewResponse(
                candidate.credentialId(),
                candidate.credentialName(),
                candidate.providerType(),
                candidate.siteProfileId(),
                candidate.modelName(),
                candidate.modelKey(),
                evaluation.eligible(),
                evaluation.healthState(),
                routeCandidate.bindingPriority(),
                routeCandidate.bindingWeight(),
                evaluation.totalScore(),
                evaluation.scoreBreakdown(),
                evaluation.exclusionReasons()
        );
    }
}
