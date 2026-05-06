package com.prodigalgal.xaigateway.gateway.core.cli;

public record CloudCliRequestFilterHit(
        String ruleId,
        String action,
        String target,
        String path,
        String summary
) {
}
