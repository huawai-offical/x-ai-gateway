package com.prodigalgal.xaigateway.gateway.core.routing;

import java.time.Duration;
import java.util.Optional;

public interface HealthStateStore {

    Optional<CredentialHealthState> getCredentialState(Long credentialId);

    void markCooldown(Long credentialId, String reason, Duration ttl);

    void clear(Long credentialId);
}
