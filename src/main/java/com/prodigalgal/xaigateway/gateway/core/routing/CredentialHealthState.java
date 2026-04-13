package com.prodigalgal.xaigateway.gateway.core.routing;

import java.time.Instant;

public record CredentialHealthState(
        String state,
        String reason,
        Instant cooldownUntil
) {
}
