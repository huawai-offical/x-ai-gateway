package com.prodigalgal.xaigateway.gateway.core.cli;

import com.prodigalgal.xaigateway.gateway.core.canonical.CanonicalRequest;
import java.util.List;

public record CloudCliRequestFilterResult(
        CanonicalRequest request,
        List<String> appliedRuleIds,
        List<String> skippedRuleIds
) {
}
