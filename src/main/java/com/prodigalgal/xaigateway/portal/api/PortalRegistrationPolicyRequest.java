package com.prodigalgal.xaigateway.portal.api;

import java.util.List;

public record PortalRegistrationPolicyRequest(
        List<String> allowedEmailDomains,
        List<String> allowedRegistrationChannels,
        Boolean inviteCodeRequired,
        List<String> inviteCodes,
        Boolean emailVerificationRequiredForKeyCreation
) {
    public PortalRegistrationPolicyRequest(
            List<String> allowedEmailDomains,
            Boolean inviteCodeRequired,
            List<String> inviteCodes,
            Boolean emailVerificationRequiredForKeyCreation) {
        this(
                allowedEmailDomains,
                null,
                inviteCodeRequired,
                inviteCodes,
                emailVerificationRequiredForKeyCreation
        );
    }
}
