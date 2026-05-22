package com.prodigalgal.xaigateway.gateway.core.model;

import com.prodigalgal.xaigateway.gateway.core.routing.RouteCandidateView;
import java.util.List;

public record ModelPolicyCandidateDecision(
        RouteCandidateView candidate,
        boolean allowed,
        List<String> exclusionReasons,
        List<String> scoreNotes
) {
}
