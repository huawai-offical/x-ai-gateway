package com.prodigalgal.xaigateway.portal.api;

import java.util.List;

public record PortalRegistrationPolicyRequest(
        List<String> allowedEmailDomains,
        Boolean inviteCodeRequired,
        List<String> inviteCodes,
        Boolean emailVerificationRequiredForKeyCreation
) {
}
