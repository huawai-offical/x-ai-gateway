package com.prodigalgal.xaigateway.portal.api;

import java.util.List;

public record PortalPasskeyRegistrationFinishRequest(
        String challengeId,
        String credentialId,
        String credentialName,
        String clientDataJson,
        String publicKeyPem,
        List<String> transports
) {
}
