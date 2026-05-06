package com.prodigalgal.xaigateway.gateway.core.cli;

import java.util.List;

public record CloudCliRequestFilterRule(
        String ruleId,
        CloudCliRequestFilterAction action,
        List<String> clientFamilies,
        String role,
        String contains,
        String replacement
) {
}
