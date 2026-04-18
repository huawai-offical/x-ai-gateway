package com.prodigalgal.xaigateway.gateway.core.governance;

public interface GovernancePolicyEngine {

    GovernanceDecision evaluate(GovernanceContext context);

    static GovernancePolicyEngine allowAll() {
        return context -> GovernanceDecision.allow();
    }
}
