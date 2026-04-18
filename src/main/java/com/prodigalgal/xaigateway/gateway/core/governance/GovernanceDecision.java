package com.prodigalgal.xaigateway.gateway.core.governance;

import java.time.Instant;
import java.util.List;

public record GovernanceDecision(
        boolean allowed,
        String healthState,
        String reason,
        GovernanceActionType actionType,
        Instant effectiveUntil,
        List<Long> matchedPolicyIds,
        List<Long> matchedQuarantineIds
) {

    public static GovernanceDecision allow() {
        return new GovernanceDecision(
                true,
                "HEALTHY",
                null,
                GovernanceActionType.NONE,
                null,
                List.of(),
                List.of()
        );
    }
}
