package com.prodigalgal.xaigateway.admin.application.operations;

import java.util.List;

public record ChangePreflightResult(
        String riskLevel,
        List<ChangePreflightCheck> checks
) {
    public ChangePreflightResult {
        checks = checks == null ? List.of() : List.copyOf(checks);
    }

    public boolean passed() {
        return checks.stream().noneMatch(item -> item.blocking() && !"OK".equalsIgnoreCase(item.status()));
    }
}
