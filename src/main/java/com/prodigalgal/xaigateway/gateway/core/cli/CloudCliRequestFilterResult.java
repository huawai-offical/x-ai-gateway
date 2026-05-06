package com.prodigalgal.xaigateway.gateway.core.cli;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import java.util.List;

public record CloudCliRequestFilterResult(
        CanonicalRequest request,
        List<String> appliedRuleIds,
        List<String> skippedRuleIds,
        List<CloudCliRequestFilterHit> hits,
        boolean denied,
        String denyRuleId,
        String denyReason
) {

    public CloudCliRequestFilterResult(
            CanonicalRequest request,
            List<String> appliedRuleIds,
            List<String> skippedRuleIds) {
        this(request, appliedRuleIds, skippedRuleIds, List.of(), false, null, null);
    }
}
