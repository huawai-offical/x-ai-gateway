package com.prodigalgal.xaigateway.portal.api;

import java.time.Instant;
import java.util.List;

public record PortalRegistrationPolicyResponse(
        List<String> allowedEmailDomains,
        List<String> allowedRegistrationChannels,
        boolean inviteCodeRequired,
        boolean inviteCodesConfigured,
        boolean emailVerificationRequiredForKeyCreation,
        Instant updatedAt
) {
    public PortalRegistrationPolicyResponse {
        allowedEmailDomains = allowedEmailDomains == null ? List.of() : List.copyOf(allowedEmailDomains);
        allowedRegistrationChannels = allowedRegistrationChannels == null ? List.of() : List.copyOf(allowedRegistrationChannels);
    }

    public PortalRegistrationPolicyResponse(
            List<String> allowedEmailDomains,
            boolean inviteCodeRequired,
            boolean inviteCodesConfigured,
            boolean emailVerificationRequiredForKeyCreation,
            Instant updatedAt) {
        this(
                allowedEmailDomains,
                List.of("PASSWORD", "INVITE_CODE"),
                inviteCodeRequired,
                inviteCodesConfigured,
                emailVerificationRequiredForKeyCreation,
                updatedAt
        );
    }
}
