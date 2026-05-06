package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;
import java.util.List;

public record PortalRegistrationPolicyResponse(
        List<String> allowedEmailDomains,
        boolean inviteCodeRequired,
        boolean inviteCodesConfigured,
        boolean emailVerificationRequiredForKeyCreation,
        Instant updatedAt
) {
}
